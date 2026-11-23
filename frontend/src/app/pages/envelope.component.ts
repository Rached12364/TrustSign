import { Component, OnInit, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { Envelope, VerifyResponse } from '../core/models';
import { STATUS_LABEL, shortHash } from '../core/labels';
import { ReportComponent } from './report.component';

@Component({
  selector: 'app-envelope',
  standalone: true,
  imports: [DatePipe, RouterLink, ReportComponent],
  template: `
  <div class="stack">
    <a routerLink="/dossiers" class="back">← Tous les dossiers</a>
    @if (env(); as e) {
      <div class="head">
        <div>
          <p class="eyebrow">Dossier n° {{ e.id }}</p>
          <h1>{{ e.title }}</h1>
          <p class="muted">{{ e.fileName }} · créé le {{ e.createdAt | date:'dd/MM/yyyy à HH:mm' }} par {{ e.createdBy }}</p>
        </div>
        <span class="pill big" [attr.data-s]="e.status">{{ statusLabel(e) }}</span>
      </div>

      <div class="detail-grid">
        <section class="card pad">
          <p class="eyebrow">Circuit de signature</p>
          <ol class="timeline">
            @for (s of e.steps; track s.position) {
              <li [class.done]="s.signedAt" [class.now]="s.current">
                <span class="t-dot">{{ s.signedAt ? '✓' : s.position }}</span>
                <div>
                  <b>{{ s.displayName }}</b>
                  @if (s.signedAt) {
                    <small>Signé le {{ s.signedAt | date:'dd/MM/yyyy à HH:mm:ss' }} · certificat <code>{{ s.certificateSerial }}</code></small>
                  } @else if (s.current) {
                    <small>Doit signer maintenant</small>
                  } @else {
                    <small>En attente des signataires précédents</small>
                  }
                </div>
              </li>
            }
          </ol>

          @if (e.myTurn) {
            <div class="sign-box">
              <label class="check"><input type="checkbox" [checked]="consent()" (change)="consent.set(!consent())">
                J'ai lu le document et je le signe électroniquement avec mon certificat personnel.</label>
              <button class="btn primary" [disabled]="!consent() || busy()" (click)="sign(e)">{{ busy() ? 'Signature…' : 'Signer le document' }}</button>
            </div>
          }
          @if (error()) { <p class="form-error">{{ error() }}</p> }
        </section>

        <section class="card pad">
          <p class="eyebrow">Preuves</p>
          <dl class="proofs">
            <dt>Empreinte de l'original</dt><dd><code [title]="e.originalSha256">{{ short(e.originalSha256) }}</code></dd>
            <dt>Empreinte actuelle</dt><dd><code [title]="e.currentSha256">{{ short(e.currentSha256) }}</code></dd>
            <dt>Signatures</dt><dd>{{ e.signedCount }} / {{ e.steps.length }}</dd>
            <dt>Archive</dt><dd>{{ e.archivePosition ? 'Entrée n° ' + e.archivePosition + ' du registre' : 'Pas encore archivé' }}</dd>
          </dl>
          <div class="actions">
            <button class="btn" (click)="download(e)">Télécharger le PDF</button>
            <button class="btn" (click)="check(e)" [disabled]="checking()">{{ checking() ? 'Vérification…' : 'Vérifier les signatures' }}</button>
          </div>
        </section>
      </div>

      @if (report()) { <app-report [data]="report()" /> }
    } @else if (error()) {
      <p class="form-error">{{ error() }}</p>
    }
  </div>
  `
})
export class EnvelopeComponent implements OnInit {
  id = input.required<string>();
  env = signal<Envelope | null>(null);
  report = signal<VerifyResponse | null>(null);
  consent = signal(false);
  busy = signal(false);
  checking = signal(false);
  error = signal('');
  short = shortHash;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.envelope(+this.id()).subscribe({
      next: e => this.env.set(e),
      error: err => this.error.set(err?.error?.message ?? 'Dossier introuvable.')
    });
  }

  statusLabel(e: Envelope): string {
    return STATUS_LABEL[e.status];
  }

  sign(e: Envelope): void {
    this.busy.set(true);
    this.error.set('');
    this.api.sign(e.id).subscribe({
      next: updated => { this.env.set(updated); this.busy.set(false); this.consent.set(false); this.check(updated); },
      error: err => { this.error.set(err?.error?.message ?? 'Signature impossible.'); this.busy.set(false); }
    });
  }

  download(e: Envelope): void {
    this.api.download(e.id).subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = e.fileName.replace(/\.pdf$/i, '') + (e.status === 'SIGNE_ARCHIVE' ? '_signe.pdf' : '.pdf');
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    });
  }

  check(e: Envelope): void {
    this.checking.set(true);
    this.api.download(e.id).subscribe(blob => {
      const file = new File([blob], e.fileName, { type: 'application/pdf' });
      this.api.verify(file).subscribe({
        next: r => { this.report.set(r); this.checking.set(false); },
        error: () => this.checking.set(false)
      });
    });
  }
}
