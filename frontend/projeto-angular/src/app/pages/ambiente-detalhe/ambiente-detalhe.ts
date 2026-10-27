import {
  Component,
  ElementRef,
  afterRenderEffect,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EMPTY, catchError, switchMap, timer } from 'rxjs';
import { SeletorScript } from '../../components/seletor-script/seletor-script';
import { Ambiente } from '../../models/ambiente.model';
import { Execucao } from '../../models/execucao.model';
import { AmbienteService } from '../../services/ambiente.service';
import { ExecucaoService } from '../../services/execucao.service';
import { ScriptService } from '../../services/script.service';
import { mensagemDeErro } from '../../utils/mensagem-erro';
import {
  dataDoBackend,
  descreverCpu,
  estaAtiva,
  ioParaMbPorSegundo,
  statusDaExecucao,
} from '../../utils/formatos';

const INTERVALO_ATUALIZACAO = 1500; // milissegundos

@Component({
  selector: 'app-ambiente-detalhe',
  imports: [DatePipe, RouterLink, SeletorScript],
  templateUrl: './ambiente-detalhe.html',
  styleUrl: './ambiente-detalhe.css',
})
export class AmbienteDetalhe {
  private readonly ambientes = inject(AmbienteService);
  private readonly execucoesApi = inject(ExecucaoService);
  private readonly scripts = inject(ScriptService);

  private readonly id = Number(inject(ActivatedRoute).snapshot.paramMap.get('id'));
  private readonly terminal = viewChild<ElementRef<HTMLPreElement>>('terminal');
  private saidaCompletaDe: number | null = null;

  readonly ambiente = signal<Ambiente | null>(null);
  readonly execucoes = signal<Execucao[]>([]);
  readonly nomesScripts = signal(new Map<number, string>());
  readonly selecionadaId = signal<number | null>(null);
  readonly saida = signal('');
  readonly scriptId = signal<number | null>(null);
  readonly iniciando = signal(false);
  readonly erro = signal('');

  readonly selecionada = computed(
    () => this.execucoes().find(e => e.id === this.selecionadaId()) ?? null
  );
  readonly temAtiva = computed(() => this.execucoes().some(estaAtiva));

  protected readonly descreverCpu = descreverCpu;
  protected readonly ioParaMbPorSegundo = ioParaMbPorSegundo;
  protected readonly dataDoBackend = dataDoBackend;
  protected readonly statusDaExecucao = statusDaExecucao;
  protected readonly estaAtiva = estaAtiva;

  constructor() {
    // Aviso enviado pela tela de criação quando o script não pôde ser iniciado.
    const estado = history.state as { erro?: string } | null;
    if (estado?.erro) {
      this.erro.set(estado.erro);
      history.replaceState({ ...estado, erro: undefined }, '');
    }

    this.ambientes.buscar(this.id).subscribe({
      next: ambiente => this.ambiente.set(ambiente),
      error: (falha: HttpErrorResponse) =>
        this.erro.set(falha.status === 404 ? 'Ambiente não encontrado.' : mensagemDeErro(falha)),
    });
    this.carregarNomesScripts();

    // Atualização automática: a cada 1,5 s busca as execuções deste ambiente.
    timer(0, INTERVALO_ATUALIZACAO)
      .pipe(
        switchMap(() => this.execucoesApi.listarPorAmbiente(this.id).pipe(catchError(() => EMPTY))),
        takeUntilDestroyed()
      )
      .subscribe(lista => this.receberExecucoes(lista));

    // Sempre que chega saída nova, rola o terminal até o fim.
    afterRenderEffect(() => {
      this.saida();
      const elemento = this.terminal()?.nativeElement;
      if (elemento) {
        elemento.scrollTop = elemento.scrollHeight;
      }
    });
  }

  nomeDoScript(scriptId: number): string {
    return this.nomesScripts().get(scriptId) ?? `Script #${scriptId}`;
  }

  selecionar(execucao: Execucao): void {
    this.selecionadaId.set(execucao.id);
    this.saida.set('');
    this.saidaCompletaDe = null;
    this.atualizarSaida(execucao);
  }

  executar(): void {
    const scriptId = this.scriptId();
    if (scriptId === null) {
      return;
    }
    this.erro.set('');
    this.iniciando.set(true);

    this.ambientes.executar(this.id, scriptId).subscribe({
      next: execucao => {
        this.iniciando.set(false);
        this.execucoes.update(lista => [execucao, ...lista.filter(e => e.id !== execucao.id)]);
        this.carregarNomesScripts();
        this.selecionar(execucao);
      },
      error: (falha: HttpErrorResponse) => {
        this.iniciando.set(false);
        this.erro.set(
          falha.status === 409
            ? 'Já existe uma execução em andamento neste ambiente. Pare-a antes de iniciar outra.'
            : mensagemDeErro(falha)
        );
      },
    });
  }

  parar(execucao: Execucao): void {
    this.erro.set('');
    this.execucoesApi.parar(execucao.id).subscribe({
      next: atualizada =>
        this.execucoes.update(lista => lista.map(e => (e.id === atualizada.id ? atualizada : e))),
      error: (falha: HttpErrorResponse) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  private receberExecucoes(lista: Execucao[]): void {
    const ordenada = [...lista].sort((a, b) => b.id - a.id);
    this.execucoes.set(ordenada);

    if (this.selecionadaId() === null && ordenada.length > 0) {
      this.selecionar(ordenada[0]);
      return;
    }
    const atual = this.selecionada();
    if (atual) {
      this.atualizarSaida(atual);
    }
  }

  // Busca a saída enquanto a execução está ativa; depois que termina, busca uma última vez e para.
  private atualizarSaida(execucao: Execucao): void {
    if (this.saidaCompletaDe === execucao.id) {
      return;
    }
    this.execucoesApi.saida(execucao.id).subscribe({
      next: texto => {
        if (this.selecionadaId() !== execucao.id) {
          return;
        }
        this.saida.set(texto);
        if (!estaAtiva(execucao)) {
          this.saidaCompletaDe = execucao.id;
        }
      },
      error: () => {
        // Falhas pontuais são ignoradas: a próxima atualização tenta de novo.
      },
    });
  }

  private carregarNomesScripts(): void {
    this.scripts.listar().subscribe({
      next: lista => this.nomesScripts.set(new Map(lista.map(s => [s.id, s.nome]))),
      error: () => {},
    });
  }
}
