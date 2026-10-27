import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';
import { Login } from './pages/login/login';
import { Cadastro } from './pages/cadastro/cadastro';
import { Ambientes } from './pages/ambientes/ambientes';
import { NovoAmbiente } from './pages/novo-ambiente/novo-ambiente';
import { AmbienteDetalhe } from './pages/ambiente-detalhe/ambiente-detalhe';

export const routes: Routes = [
  { path: 'login', component: Login },
  { path: 'cadastro', component: Cadastro },
  { path: 'ambientes', component: Ambientes, canActivate: [authGuard] },
  { path: 'ambientes/novo', component: NovoAmbiente, canActivate: [authGuard] },
  { path: 'ambientes/:id', component: AmbienteDetalhe, canActivate: [authGuard] },
  { path: '', pathMatch: 'full', redirectTo: 'ambientes' },
  { path: '**', redirectTo: 'ambientes' },
];
