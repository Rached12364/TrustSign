import { Component, computed } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { ROLE_LABEL } from '../core/labels';
import { LogoComponent } from './logo.component';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LogoComponent],
  template: `
  <header class="topbar">
    <app-logo />
    <nav>
      <a routerLink="/dossiers" routerLinkActive="active">Dossiers</a>
      <a routerLink="/verifier" routerLinkActive="active">Vérifier un PDF</a>
      @if (isAdmin()) {
        <a routerLink="/archive" routerLinkActive="active">Archive</a>
        <a routerLink="/audit" routerLinkActive="active">Journal d'audit</a>
      }
    </nav>
    <div class="who">
      <span class="user"><b>{{ auth.session()?.displayName }}</b><small>{{ roleLabel() }}</small></span>
      <button class="btn ghost" (click)="auth.logout()">Déconnexion</button>
    </div>
  </header>
  <main class="page"><router-outlet /></main>
  `
})
export class ShellComponent {
  isAdmin = computed(() => this.auth.role() === 'ADMIN');
  roleLabel = computed(() => {
    const r = this.auth.role();
    return r ? ROLE_LABEL[r] : '';
  });

  constructor(public auth: AuthService) {}
}
