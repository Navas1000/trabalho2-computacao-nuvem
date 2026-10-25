import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Script } from '../models/script.model';

@Injectable({ providedIn: 'root' })
export class ScriptService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/scripts';

  listar(): Observable<Script[]> {
    return this.http.get<Script[]>(this.url);
  }

  enviar(nome: string, arquivo: File): Observable<Script> {
    const formulario = new FormData();
    formulario.append('nome', nome);
    formulario.append('arquivo', arquivo);
    return this.http.post<Script>(`${this.url}/upload`, formulario);
  }
}
