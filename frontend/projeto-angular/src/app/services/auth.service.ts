import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, finalize, tap } from 'rxjs';
import { CadastroRequest, LoginRequest, LoginResponse, Usuario } from '../models/usuario.model';

const CHAVE_TOKEN = 'token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/auth';

  cadastrar(dados: CadastroRequest): Observable<Usuario> {
    return this.http.post<Usuario>(`${this.url}/cadastro`, dados);
  }

  entrar(dados: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.url}/login`, dados).pipe(
      tap(resposta => localStorage.setItem(CHAVE_TOKEN, resposta.token))
    );
  }

  sair(): Observable<void> {
    return this.http.post<void>(`${this.url}/logout`, {}).pipe(
      finalize(() => this.limparSessao())
    );
  }

  token(): string | null {
    return localStorage.getItem(CHAVE_TOKEN);
  }

  estaLogado(): boolean {
    return this.token() !== null;
  }

  limparSessao(): void {
    localStorage.removeItem(CHAVE_TOKEN);
  }
}
