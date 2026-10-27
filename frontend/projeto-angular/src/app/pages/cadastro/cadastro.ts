import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { mensagemDeErro } from '../../utils/mensagem-erro';

@Component({
  selector: 'app-cadastro',
  imports: [FormsModule, RouterLink],
  templateUrl: './cadastro.html',
  styleUrl: './cadastro.css',
})
export class Cadastro {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  nome = '';
  email = '';
  senha = '';
  readonly carregando = signal(false);
  readonly erro = signal('');

  cadastrar(): void {
    this.erro.set('');
    this.carregando.set(true);
    const dados = { nome: this.nome, email: this.email, senha: this.senha };

    this.auth
      .cadastrar(dados)
      .pipe(switchMap(() => this.auth.entrar({ email: dados.email, senha: dados.senha })))
      .subscribe({
        next: () => this.router.navigate(['/ambientes']),
        error: (falha: HttpErrorResponse) => {
          this.erro.set(falha.status === 409 ? 'Este e-mail já está cadastrado.' : mensagemDeErro(falha));
          this.carregando.set(false);
        },
      });
  }
}
