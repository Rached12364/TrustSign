export type Role = 'ADMIN' | 'USER';
export type EnvelopeStatus = 'EN_COURS' | 'SIGNE_ARCHIVE';
export type Verdict = 'VALIDE' | 'MODIFIE_APRES_SIGNATURE' | 'NON_FIABLE' | 'ALTERE' | 'AUCUNE_SIGNATURE';

export interface Session {
  token: string;
  username: string;
  displayName: string;
  role: Role;
}

export interface Step {
  position: number;
  username: string;
  displayName: string;
  signedAt: string | null;
  certificateSerial: string | null;
  current: boolean;
}

export interface Envelope {
  id: number;
  title: string;
  fileName: string;
  createdBy: string;
  createdAt: string;
  status: EnvelopeStatus;
  originalSha256: string;
  currentSha256: string;
  completedAt: string | null;
  steps: Step[];
  myTurn: boolean;
  signedCount: number;
  archivePosition: number | null;
}

export interface SignerOption {
  username: string;
  displayName: string;
}

export interface SignatureCheck {
  index: number;
  signer: string;
  signerUid: string | null;
  certSerial: string | null;
  issuer: string | null;
  signedAt: string | null;
  reason: string | null;
  location: string | null;
  integrityOk: boolean;
  trusted: boolean;
  certValidAtSigning: boolean;
  coversWholeDocument: boolean;
  detail: string;
  valid: boolean;
}

export interface VerifyResponse {
  fileName: string;
  sha256: string;
  size: number;
  report: {
    signatureCount: number;
    allValid: boolean;
    modifiedAfterLastSignature: boolean;
    verdict: Verdict;
    signatures: SignatureCheck[];
  };
}

export interface EntryCheck {
  position: number;
  envelopeId: number;
  title: string;
  documentSha256: string;
  previousHash: string;
  entryHash: string;
  archivedAt: string;
  linkOk: boolean;
  hashOk: boolean;
  fileOk: boolean;
  ok: boolean;
}

export interface ChainReport {
  entries: number;
  intact: boolean;
  firstBrokenPosition: number | null;
  message: string;
  checks: EntryCheck[];
}

export interface AuditEntry {
  id: number;
  time: string;
  username: string;
  role: Role | null;
  action: string;
  detail: string;
}
