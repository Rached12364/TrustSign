import { EnvelopeStatus, Role, Verdict } from './models';

export const ROLE_LABEL: Record<Role, string> = {
  ADMIN: 'Administrateur',
  USER: 'Utilisateur'
};

export const STATUS_LABEL: Record<EnvelopeStatus, string> = {
  EN_COURS: 'En cours',
  SIGNE_ARCHIVE: 'Signé et archivé'
};

export const VERDICT: Record<Verdict, { title: string; text: string; tone: 'ok' | 'warn' | 'bad' }> = {
  VALIDE: { title: 'Document valide', text: 'Toutes les signatures sont intactes et émises par l\'autorité de confiance. Rien n\'a été modifié depuis la dernière signature.', tone: 'ok' },
  MODIFIE_APRES_SIGNATURE: { title: 'Modifié après signature', text: 'Les signatures sont intactes, mais des données ont été ajoutées au fichier après la dernière signature.', tone: 'warn' },
  NON_FIABLE: { title: 'Signataire non reconnu', text: 'La signature est intacte, mais le certificat n\'a pas été émis par l\'autorité de confiance, ou n\'était pas valide.', tone: 'warn' },
  ALTERE: { title: 'Document altéré', text: 'Le contenu signé a été modifié : l\'empreinte ne correspond plus à la signature.', tone: 'bad' },
  AUCUNE_SIGNATURE: { title: 'Aucune signature', text: 'Ce PDF ne contient aucune signature électronique.', tone: 'warn' }
};

export function shortHash(h: string | null | undefined): string {
  return h ? `${h.slice(0, 10)}…${h.slice(-6)}` : '';
}
