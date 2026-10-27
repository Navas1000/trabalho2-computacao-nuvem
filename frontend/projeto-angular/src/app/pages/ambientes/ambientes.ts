import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { Ambiente } from '../../models/ambiente.model';
import { AmbienteService } from '../../services/ambiente.service';
import { ExecucaoService } from '../../services/execucao.service';
import { mensagemDeErro } from '../../utils/mensagem-erro';
import {
  StatusAmbiente,
  dataDoBackend,
  descreverCpu,
  ioParaMbPorSegundo,
  statusDoAmbiente,
} from '../../utils/formatos';

interface LinhaAmbiente {
  ambiente: Ambiente;
  criadoEm: Date;
  status: StatusAmbiente;
}

@Component({
  selector: 'app-ambientes',
  imports: [RouterLink, DatePipe],
  templateUrl: './ambientes.html',
  styleUrl: './ambientes.css',
})
export class Ambientes {
  private readonly ambientes = inject(AmbienteService);
  private readonly execucoes = inject(ExecucaoService);

  readonly lista = signal<LinhaAmbiente[]>([]);
  readonly carregando = signal(true);
  readonly erro = signal('');
  readonly sucesso = signal('');

  protected readonly descreverCpu = descreverCpu;
  protected readonly ioParaMbPorSegundo = ioParaMbPorSegundo;

  constructor() {
    // Mensagem enviada pela tela de criação ao voltar para a lista.
    const estado = history.state as { mensagem?: string } | null;
    if (estado?.mensagem) {
      this.sucesso.set(estado.mensagem);
      history.replaceState({ ...estado, mensagem: undefined }, '');
    }
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);

    forkJoin({ ambientes: this.ambientes.listar(), execucoes: this.execucoes.listar() }).subscribe({
      next: ({ ambientes, execucoes }) => {
        this.lista.set(
          ambientes.map(ambiente => ({
            ambiente,
            criadoEm: dataDoBackend(ambiente.criado_em),
            status: statusDoAmbiente(execucoes.filter(e => e.ambiente_id === ambiente.id)),
          }))
        );
        this.carregando.set(false);
      },
      error: (falha: HttpErrorResponse) => {
        this.erro.set(mensagemDeErro(falha));
        this.carregando.set(false);
      },
    });
  }

  remover(ambiente: Ambiente): void {
    if (!confirm(`Remover o ambiente "${ambiente.nome}"?`)) {
      return;
    }
    this.erro.set('');
    this.sucesso.set('');

    this.ambientes.remover(ambiente.id).subscribe({
      next: () => {
        this.sucesso.set(`Ambiente "${ambiente.nome}" removido.`);
        this.carregar();
      },
      error: (falha: HttpErrorResponse) => {
        this.erro.set(
          falha.status === 409
            ? 'Este ambiente tem uma execução em andamento. Pare a execução antes de remover.'
            : mensagemDeErro(falha)
        );
      },
    });
  }
}
