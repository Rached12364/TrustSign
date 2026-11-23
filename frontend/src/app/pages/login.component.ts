import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { LogoComponent } from './logo.component';

interface DemoAccount { user: string; label: string; scope: string; }

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, LogoComponent],
  template: `
  <div class="login">
    <section class="login-intro">
      <app-logo [light]="true" />
      <h1>Signer, vérifier, archiver.<br>Avec une preuve à chaque étape.</h1>
      <ul class="pillars">
        <li><span class="pi">1</span><div><b>Signature PAdES</b><p>Standard européen des PDF signés, avec un certificat X.509 par signataire.</p></div></li>
        <li><span class="pi">2</span><div><b>Circuit ordonné</b><p>Chaque signataire signe à son tour ; personne ne peut sauter une étape.</p></div></li>
        <li><span class="pi">3</span><div><b>Vérification</b><p>Déposez un PDF : qui a signé, avec quel certificat, et a-t-il été modifié ?</p></div></li>
        <li><span class="pi">4</span><div><b>Archive infalsifiable</b><p>Registre chaîné par empreintes SHA-256 : toute retouche est détectée.</p></div></li>
      </ul>
    </section>

    <section class="login-form">
      <form (ngSubmit)="submit()" class="card">
        <h2>Connexion</h2>
        <label>Identifiant
          <input name="u" [(ngModel)]="username" autocomplete="username" required>
        </label>
        <label>Mot de passe
          <input name="p" type="password" [(ngModel)]="password" autocomplete="current-password" required>
        </label>
        @if (error()) { <p class="form-error">{{ error() }}</p> }
        <button class="btn primary wide" [disabled]="loading()">{{ loading() ? 'Connexion…' : 'Se connecter' }}</button>

        <div class="demo">
          <p class="eyebrow">Comptes de démonstration</p>
          @for (a of accounts; track a.user) {
            <button type="button" class="demo-acc" (click)="fill(a)">
              <span><b>{{ a.label }}</b><small>{{ a.user }} / {{ a.user }}123</small></span>
              <em>{{ a.scope }}</em>
            </button>
          }
        </div>
      </form>
    </section>
  </div>
  `
})
export class LoginComponent {
  username = '';
  password = '';
  loading = signal(false);
  error = signal('');

  accounts: DemoAccount[] = [
    { user: 'alice', label: 'Alice Martin', scope: 'Signataire' },
    { user: 'bob', label: 'Bob Durand', scope: 'Signataire' },
    { user: 'claire', label: 'Claire Petit', scope: 'Signataire' },
    { user: 'admin', label: 'Administrateur', scope: 'Archive + audit' }
  ];

  constructor(private auth: AuthService, private router: Router) {
    if (auth.isLoggedIn()) router.navigate(['/dossiers']);
  }

  fill(a: DemoAccount): void {
    this.username = a.user;
    this.password = a.user + '123';
    this.submit();
  }

  submit(): void {
    if (!this.username || !this.password) return;
    this.loading.set(true);
    this.error.set('');
    this.auth.login(this.username, this.password).subscribe({
      next: () => this.router.navigate(['/dossiers']),
      error: () => {
        this.error.set('Identifiant ou mot de passe incorrect.');
        this.loading.set(false);
      }
    });
  }
}
