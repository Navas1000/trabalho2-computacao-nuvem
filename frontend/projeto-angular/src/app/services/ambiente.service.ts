import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Ambiente, AmbienteRequest } from '../models/ambiente.model';
import { Execucao } from '../models/execucao.model';

@Injectable({ providedIn: 'root' })
export class AmbienteService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/ambientes';

  listar(): Observable<Ambiente[]> {
    return this.http.get<Ambiente[]>(this.url);
  }

  buscar(id: number): Observable<Ambiente> {
    return this.http.get<Ambiente>(`${this.url}/${id}`);
  }

  criar(dados: AmbienteRequest): Observable<Ambiente> {
    return this.http.post<Ambiente>(this.url, dados);
  }

  remover(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  executar(id: number, scriptId: number): Observable<Execucao> {
    return this.http.post<Execucao>(`${this.url}/${id}/execucoes`, { scriptId });
  }
}
