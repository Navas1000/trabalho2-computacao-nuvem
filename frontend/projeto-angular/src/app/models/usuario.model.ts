export interface Usuario {
  id: number;
  nome: string;
  email: string;
}

export interface LoginRequest {
  email: string;
  senha: string;
}

export interface CadastroRequest {
  nome: string;
  email: string;
  senha: string;
}

export interface LoginResponse {
  token: string;
  tipo: string;
  expiraEm: string;
  usuario: Usuario;
}
