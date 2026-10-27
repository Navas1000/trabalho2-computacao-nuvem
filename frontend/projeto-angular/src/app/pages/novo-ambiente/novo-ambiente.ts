import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { SeletorScript } from '../../components/seletor-script/seletor-script';
import { AmbienteService } from '../../services/ambiente.service';
import { mensagemDeErro } from '../../utils/mensagem-erro';
import { mbPorSegundoParaIo, percentualParaCpu } from '../../utils/formatos';

@Component({
  selector: 'app-novo-ambiente',
  imports: [FormsModule, RouterLink, SeletorScript],
  templateUrl: './novo-ambiente.html',
  styleUrl: './novo-ambiente.css',
})
export class NovoAmbiente {
  private readonly ambientes = inject(AmbienteService);
  private readonly router = inject(Router);

  nome = '';
  cpuPercentual = 50;
  memoriaMb = 256;
  ioMbPorSegundo = 10;
  readonly scriptId = signal<number | null>(null);

  readonly salvando = signal(false);
  readonly erro = signal('');

  protected readonly percentualParaCpu = percentualParaCpu;

  criar(): void {
    this.erro.set('');
    this.salvando.set(true);

    const dados = {
      nome: this.nome.trim(),
      cpuMillicores: percentualParaCpu(this.cpuPercentual),
      memoriaMb: Math.round(this.memoriaMb),
      ioBytesSegundo: mbPorSegundoParaIo(this.ioMbPorSegundo),
    };

    this.ambientes.criar(dados).subscribe({
      next: ambiente => {
        const scriptId = this.scriptId();
        if (scriptId === null) {
          this.router.navigate(['/ambientes'], {
            state: { mensagem: `Ambiente "${ambiente.nome}" criado com sucesso.` },
          });
          return;
        }
        // Com script escolhido, o ambiente já é executado e o usuário vai para a tela de execução.
        this.ambientes.executar(ambiente.id, scriptId).subscribe({
          next: () => this.router.navigate(['/ambientes', ambiente.id]),
          error: () =>
            this.router.navigate(['/ambientes', ambiente.id], {
              state: { erro: 'O ambiente foi criado, mas não foi possível iniciar o script.' },
            }),
        });
      },
      error: (falha: HttpErrorResponse) => {
        this.erro.set(mensagemDeErro(falha));
        this.salvando.set(false);
      },
    });
  }
}
