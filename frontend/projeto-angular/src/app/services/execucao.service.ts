import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { Execucao } from '../models/execucao.model';

@Injectable({ providedIn: 'root' })
export class ExecucaoService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/execucoes';

  listar(): Observable<Execucao[]> {
    return this.http.get<Execucao[]>(this.url);
  }

  listarPorAmbiente(ambienteId: number): Observable<Execucao[]> {
    return this.listar().pipe(
      map(lista => lista.filter(execucao => execucao.ambiente_id === ambienteId))
    );
  }

  buscar(id: number): Observable<Execucao> {
    return this.http.get<Execucao>(`${this.url}/${id}`);
  }

  parar(id: number): Observable<Execucao> {
    return this.http.post<Execucao>(`${this.url}/${id}/parar`, {});
  }

  saida(id: number): Observable<string> {
    return this.http.get(`${this.url}/${id}/saida`, { responseType: 'text' });
  }
}
