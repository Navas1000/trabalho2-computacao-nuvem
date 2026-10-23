import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (requisicao, proximo) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.token();

  const comToken = token
    ? requisicao.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : requisicao;

  return proximo(comToken).pipe(
    catchError((erro: HttpErrorResponse) => {
      if (erro.status === 401 && !requisicao.url.includes('/api/auth/')) {
        auth.limparSessao();
        router.navigate(['/login']);
      }
      return throwError(() => erro);
    })
  );
};
