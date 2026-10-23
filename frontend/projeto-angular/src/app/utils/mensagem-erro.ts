import { HttpErrorResponse } from '@angular/common/http';

export function mensagemDeErro(falha: HttpErrorResponse): string {
  const corpo = falha.error;
  if (corpo && typeof corpo === 'object' && typeof corpo.erro === 'string') {
    const campos = Array.isArray(corpo.campos) ? ` (${corpo.campos.join('; ')})` : '';
    return corpo.erro + campos;
  }

  switch (falha.status) {
    case 0:
      return 'Não foi possível conectar ao servidor.';
    case 400:
      return 'Dados inválidos.';
    case 401:
      return 'Sessão expirada. Faça login novamente.';
    case 403:
      return 'Acesso negado.';
    case 404:
      return 'Registro não encontrado.';
    case 409:
      return 'A operação conflita com dados existentes.';
    default:
      return 'Erro inesperado no servidor.';
  }
}
