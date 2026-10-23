export type StatusExecucao =
  | 'PENDENTE'
  | 'EM_EXECUCAO'
  | 'PARADA_SOLICITADA'
  | 'TERMINADO'
  | 'ERRO'
  | 'CANCELADO';

export interface Execucao {
  id: number;
  usuario_id: number;
  ambiente_id: number;
  script_id: number;
  status: StatusExecucao;
  codigo_saida: number | null;
  criado_em: string;
  atualizado_em: string;
}
