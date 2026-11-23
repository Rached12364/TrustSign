import { Component, computed, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { VerifyResponse } from '../core/models';
import { VERDICT, shortHash } from '../core/labels';

/** Affiche le rapport de vérification d'un PDF signé. */
@Component({
  selector: 'app-report',
  standalone: true,
  imports: [DatePipe],
  template: `
  @if (data(); as d) {
    <div class="verdict" [attr.data-t]="v().tone">
      <div class="v-icon">{{ v().tone === 'ok' ? '✓' : v().tone === 'warn' ? '!' : '✕' }}</div>
      <div>
        <b>{{ v().title }}</b>
        <p>{{ v().text }}</p>
        <small>{{ d.fileName }} · {{ (d.size / 1024).toFixed(1) }} Ko · SHA-256 <code [title]="d.sha256">{{ short(d.sha256) }}</code></small>
      </div>
    </div>

    @for (s of d.report.signatures; track s.index) {
      <article class="sig card">
        <header>
          <span class="sig-n">{{ s.index }}</span>
          <div>
            <b>{{ s.signer }}</b>
            <small>{{ s.signedAt ? (s.signedAt | date:'dd/MM/yyyy à HH:mm:ss') : 'date inconnue' }}{{ s.location ? ' · ' + s.location : '' }}</small>
          </div>
          <span class="pill" [attr.data-ok]="s.valid">{{ s.valid ? 'Signature valide' : 'Signature invalide' }}</span>
        </header>
        <ul class="checks">
          <li [class.ok]="s.integrityOk"><span>{{ s.integrityOk ? '✓' : '✕' }}</span> Intégrité : {{ s.integrityOk ? 'contenu signé inchangé' : 'contenu signé modifié' }}</li>
          <li [class.ok]="s.trusted"><span>{{ s.trusted ? '✓' : '✕' }}</span> {{ s.trusted ? 'Certificat émis par l’autorité de confiance' : 'Certificat non reconnu par l’autorité de confiance' }}{{ s.issuer ? ' (' + s.issuer + ')' : '' }}</li>
          <li [class.ok]="s.certValidAtSigning"><span>{{ s.certValidAtSigning ? '✓' : '✕' }}</span> Certificat {{ s.certValidAtSigning ? 'valide' : 'non valide' }} à la date de signature</li>
          <li [class.ok]="s.coversWholeDocument" [class.info]="!s.coversWholeDocument && s.index < d.report.signatureCount">
            <span>{{ s.coversWholeDocument ? '✓' : (s.index < d.report.signatureCount ? '→' : '!') }}</span>
            {{ s.coversWholeDocument ? 'Couvre tout le fichier' : (s.index < d.report.signatureCount ? 'Couvre la version signée à ce moment (des signatures ont suivi)' : 'Des données ont été ajoutées après cette signature') }}
          </li>
        </ul>
        <small class="meta">Certificat n° <code>{{ s.certSerial ?? '—' }}</code>{{ s.reason ? ' · ' + s.reason : '' }}</small>
        @if (s.detail) { <p class="detail">{{ s.detail }}</p> }
      </article>
    }
  }
  `
})
export class ReportComponent {
  data = input<VerifyResponse | null>(null);
  v = computed(() => VERDICT[this.data()?.report.verdict ?? 'AUCUNE_SIGNATURE']);
  short = shortHash;
}
