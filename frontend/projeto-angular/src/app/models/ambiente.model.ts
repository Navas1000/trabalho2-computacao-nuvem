export interface Ambiente {
  id: number;
  usuario_id: number;
  nome: string;
  cpu_millicores: number;
  memoria_mb: number;
  io_bytes_segundo: number;
  criado_em: string;
}

export interface AmbienteRequest {
  nome: string;
  cpuMillicores: number;
  memoriaMb: number;
  ioBytesSegundo: number;
}
