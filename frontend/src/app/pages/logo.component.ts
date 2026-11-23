import { Component, input } from '@angular/core';

@Component({
  selector: 'app-logo',
  standalone: true,
  template: `
  <span class="logo" [class.light]="light()">
    <svg viewBox="0 0 32 32" width="30" height="30" aria-hidden="true">
      <path d="M16 2 4 7v8c0 7.5 5.1 13.6 12 15 6.9-1.4 12-7.5 12-15V7L16 2z" class="logo-shield"/>
      <path d="M9 19c2-4 3.5-6 4.5-6s.5 4 1.5 4 2-3 3-3 1 2 2 2 1.5-1 2.5-1" fill="none" class="logo-check"
            stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>
    </svg>
    <span class="logo-word">Trust<b>Sign</b></span>
  </span>
  `
})
export class LogoComponent {
  light = input(false);
}
