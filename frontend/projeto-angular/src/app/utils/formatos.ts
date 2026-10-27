import { Execucao, StatusExecucao } from '../models/execucao.model';

export type ClasseStatus = 'pendente' | 'executando' | 'parando' | 'sucesso' | 'erro' | 'neutro';

export interface StatusVisual {
  rotulo: string;
  classe: ClasseStatus;
}

export interface StatusAmbiente extends StatusVisual {
  atualizadoEm: Date | null;
}

const BYTES_POR_MB = 1024 * 1024;
const STATUS_ATIVOS: StatusExecucao[] = ['PENDENTE', 'EM_EXECUCAO', 'PARADA_SOLICITADA'];

// O backend guarda CPU em millicores: 1000 millicores = 100% de um núcleo.
export function cpuParaPercentual(millicores: number): number {
  return millicores / 10;
}

export function percentualParaCpu(percentual: number): number {
  return Math.round(percentual * 10);
}

export function descreverCpu(millicores: number): string {
  return `${cpuParaPercentual(millicores)}% (${millicores}m)`;
}

// O backend guarda o limite de I/O em bytes por segundo.
export function ioParaMbPorSegundo(bytes: number): number {
  return Math.round((bytes / BYTES_POR_MB) * 100) / 100;
}

export function mbPorSegundoParaIo(mb: number): number {
  return Math.round(mb * BYTES_POR_MB);
}

// O backend envia datas em UTC sem indicar o fuso; o "Z" faz o navegador converter para o horário local.
export function dataDoBackend(valor: string): Date {
  const temFuso = /([zZ]|[+-]\d{2}:\d{2})$/.test(valor);
  return new Date(temFuso ? valor : `${valor}Z`);
}

export function estaAtiva(execucao: Execucao): boolean {
  return STATUS_ATIVOS.includes(execucao.status);
}

function comCodigo(rotulo: string, codigo: number | null, ocultarZero = false): string {
  if (codigo === null || (ocultarZero && codigo === 0)) {
    return rotulo;
  }
  return `${rotulo} (código ${codigo})`;
}

export function statusDaExecucao(execucao: Pick<Execucao, 'status' | 'codigo_saida'>): StatusVisual {
  switch (execucao.status) {
    case 'PENDENTE':
      return { rotulo: 'Pendente', classe: 'pendente' };
    case 'EM_EXECUCAO':
      return { rotulo: 'Executando', classe: 'executando' };
    case 'PARADA_SOLICITADA':
      return { rotulo: 'Parando', classe: 'parando' };
    case 'TERMINADO':
      return { rotulo: comCodigo('Terminado', execucao.codigo_saida, true), classe: 'sucesso' };
    case 'ERRO':
      return { rotulo: comCodigo('Erro', execucao.codigo_saida), classe: 'erro' };
    case 'CANCELADO':
      return { rotulo: 'Cancelado', classe: 'neutro' };
  }
}

// O ambiente não tem status próprio no banco: ele é calculado a partir das execuções.
export function statusDoAmbiente(execucoes: Execucao[]): StatusAmbiente {
  if (execucoes.length === 0) {
    return { rotulo: 'Sem execuções', classe: 'neutro', atualizadoEm: null };
  }
  const referencia = execucoes.find(estaAtiva) ?? execucoes.reduce((a, b) => (b.id > a.id ? b : a));
  return { ...statusDaExecucao(referencia), atualizadoEm: dataDoBackend(referencia.atualizado_em) };
}
