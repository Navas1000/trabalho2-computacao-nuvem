import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { mensagemDeErro } from '../../utils/mensagem-erro';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  email = '';
  senha = '';
  readonly carregando = signal(false);
  readonly erro = signal('');

  entrar(): void {
    this.erro.set('');
    this.carregando.set(true);

    this.auth.entrar({ email: this.email, senha: this.senha }).subscribe({
      next: () => this.router.navigate(['/ambientes']),
      error: (falha: HttpErrorResponse) => {
        this.erro.set(falha.status === 401 ? 'E-mail ou senha inválidos.' : mensagemDeErro(falha));
        this.carregando.set(false);
      },
    });
  }
}
