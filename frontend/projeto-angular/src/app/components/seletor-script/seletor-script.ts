import { Component, inject, input, model, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Script } from '../../models/script.model';
import { ScriptService } from '../../services/script.service';
import { mensagemDeErro } from '../../utils/mensagem-erro';

const TAMANHO_MAXIMO = 256 * 1024; // mesmo limite do backend (256 KB)

@Component({
  selector: 'app-seletor-script',
  imports: [FormsModule],
  templateUrl: './seletor-script.html',
  styleUrl: './seletor-script.css',
})
export class SeletorScript {
  private readonly scripts = inject(ScriptService);

  // Ligação de mão dupla: a tela que usa o componente lê e altera o script escolhido.
  readonly scriptId = model<number | null>(null);
  readonly obrigatorio = input(false);

  readonly lista = signal<Script[]>([]);
  readonly enviando = signal(false);
  readonly erro = signal('');
  readonly sucesso = signal('');

  nomeNovo = '';
  private arquivo: File | null = null;

  constructor() {
    this.carregar();
  }

  carregar(): void {
    this.scripts.listar().subscribe({
      next: lista => this.lista.set(lista),
      error: (falha: HttpErrorResponse) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  escolherArquivo(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    this.arquivo = campo.files?.[0] ?? null;
    if (this.arquivo && !this.nomeNovo) {
      this.nomeNovo = this.arquivo.name;
    }
  }

  enviar(campo: HTMLInputElement): void {
    this.erro.set('');
    this.sucesso.set('');
    const nome = this.nomeNovo.trim();

    if (!this.arquivo || !nome) {
      this.erro.set('Escolha um arquivo e informe um nome para o script.');
      return;
    }
    if (nome.length > 120) {
      this.erro.set('O nome do script pode ter no máximo 120 caracteres.');
      return;
    }
    if (this.arquivo.size > TAMANHO_MAXIMO) {
      this.erro.set('O arquivo pode ter no máximo 256 KB.');
      return;
    }

    this.enviando.set(true);
    this.scripts.enviar(nome, this.arquivo).subscribe({
      next: script => {
        this.lista.update(lista => [...lista, script]);
        this.scriptId.set(script.id);
        this.sucesso.set(`Script "${script.nome}" enviado e selecionado.`);
        this.nomeNovo = '';
        this.arquivo = null;
        campo.value = '';
        this.enviando.set(false);
      },
      error: (falha: HttpErrorResponse) => {
        this.erro.set(falha.status === 400 ? 'Arquivo inválido: envie um script de texto (UTF-8).' : mensagemDeErro(falha));
        this.enviando.set(false);
      },
    });
  }
}
