import { Component, signal } from '@angular/core';
import { ApiService } from '../core/api.service';
import { VerifyResponse } from '../core/models';
import { ReportComponent } from './report.component';

@Component({
  selector: 'app-verify',
  standalone: true,
  imports: [ReportComponent],
  template: `
  <div class="stack narrow">
    <div>
      <p class="eyebrow">Contrôle indépendant</p>
      <h1>Vérifier un PDF signé</h1>
      <p class="muted">Déposez n'importe quel PDF. TrustSign recalcule chaque signature : intégrité du contenu, certificat émis par l'autorité de confiance, validité à la date de signature, et ajout éventuel de données après la dernière signature.</p>
    </div>
    <label class="drop" [class.over]="over()" (dragover)="$event.preventDefault(); over.set(true)" (dragleave)="over.set(false)" (drop)="drop($event)">
      <input type="file" accept="application/pdf" (change)="pick($event)">
      <b>{{ busy() ? 'Vérification…' : 'Déposez un PDF ici' }}</b>
      <span>ou cliquez pour choisir un fichier</span>
    </label>
    @if (error()) { <p class="form-error">{{ error() }}</p> }
    <app-report [data]="result()" />
  </div>
  `
})
export class VerifyComponent {
  result = signal<VerifyResponse | null>(null);
  busy = signal(false);
  over = signal(false);
  error = signal('');

  constructor(private api: ApiService) {}

  pick(e: Event): void {
    const f = (e.target as HTMLInputElement).files?.[0];
    if (f) this.run(f);
  }

  drop(e: DragEvent): void {
    e.preventDefault();
    this.over.set(false);
    const f = e.dataTransfer?.files?.[0];
    if (f) this.run(f);
  }

  private run(f: File): void {
    this.busy.set(true);
    this.error.set('');
    this.result.set(null);
    this.api.verify(f).subscribe({
      next: r => { this.result.set(r); this.busy.set(false); },
      error: err => { this.error.set(err?.error?.message ?? 'Vérification impossible.'); this.busy.set(false); }
    });
  }
}
