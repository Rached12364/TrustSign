import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/guards';
import { LoginComponent } from './pages/login.component';
import { ShellComponent } from './pages/shell.component';
import { EnvelopesComponent } from './pages/envelopes.component';
import { EnvelopeComponent } from './pages/envelope.component';
import { VerifyComponent } from './pages/verify.component';
import { ArchiveComponent } from './pages/archive.component';
import { AuditComponent } from './pages/audit.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: 'dossiers', component: EnvelopesComponent },
      { path: 'dossiers/:id', component: EnvelopeComponent },
      { path: 'verifier', component: VerifyComponent },
      { path: 'archive', component: ArchiveComponent, canActivate: [roleGuard('ADMIN')] },
      { path: 'audit', component: AuditComponent, canActivate: [roleGuard('ADMIN')] },
      { path: '', pathMatch: 'full', redirectTo: 'dossiers' }
    ]
  },
  { path: '**', redirectTo: 'dossiers' }
];
