"""Génère les PDF de démonstration de TrustSign (entreprise fictive « Orbis Services »)."""
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.colors import HexColor
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer

ss = getSampleStyleSheet()
H1 = ParagraphStyle("h1", parent=ss["Title"], textColor=HexColor("#121C4E"), fontSize=19)
H2 = ParagraphStyle("h2", parent=ss["Heading3"], textColor=HexColor("#121C4E"))
P = ParagraphStyle("p", parent=ss["BodyText"], fontSize=10.5, leading=15)
TAG = ParagraphStyle("tag", parent=P, textColor=HexColor("#6b7280"), fontSize=9)

DOCS = {
 "contrat_prestation.pdf": ("Contrat de prestation de services", [
  ("Article 1 – Objet", "Le présent contrat a pour objet la réalisation par le Prestataire d'une application web de gestion documentaire pour le Client, selon le cahier des charges annexé."),
  ("Article 2 – Durée", "Le contrat prend effet à la date de la dernière signature et se termine à la livraison finale, prévue six mois plus tard."),
  ("Article 3 – Prix", "Le prix global et forfaitaire est fixé à 48 000 dinars hors taxes, payable en trois échéances : 30 % à la signature, 40 % à la recette intermédiaire, 30 % à la livraison."),
  ("Article 4 – Confidentialité", "Chaque partie s'engage à garder confidentielles les informations reçues de l'autre partie pendant toute la durée du contrat et trois ans après.")]),
 "accord_confidentialite.pdf": ("Accord de confidentialité", [
  ("Article 1 – Informations confidentielles", "Sont confidentielles toutes les informations techniques, commerciales ou financières échangées entre les parties, quel que soit leur support."),
  ("Article 2 – Obligations", "La partie qui reçoit les informations s'engage à ne les utiliser que pour l'évaluation du projet commun et à ne pas les divulguer à des tiers."),
  ("Article 3 – Durée", "Le présent accord est conclu pour une durée de cinq ans à compter de sa signature.")]),
 "avenant_1.pdf": ("Avenant n° 1 au contrat de prestation", [
  ("Article 1 – Modification", "Les parties conviennent d'ajouter au périmètre du contrat un module de signature électronique des documents."),
  ("Article 2 – Prix", "Le prix est augmenté de 9 500 dinars hors taxes. Les autres articles du contrat restent inchangés.")]),
 "pv_reception.pdf": ("Procès-verbal de réception", [
  ("Objet", "Le Client déclare avoir reçu la version 1.0 de l'application et constate qu'elle est conforme au cahier des charges."),
  ("Réserves", "Aucune réserve bloquante. Deux réserves mineures seront levées dans un délai de quinze jours.")]),
}

for fname, (title, sections) in DOCS.items():
    doc = SimpleDocTemplate(fname, pagesize=A4, title=title, author="Orbis Services (fictif)",
                            leftMargin=60, rightMargin=60, topMargin=60, bottomMargin=60)
    story = [Paragraph("Orbis Services — entreprise fictive de démonstration", TAG),
             Paragraph(title, H1), Spacer(1, 10)]
    for h, body in sections:
        story += [Paragraph(h, H2), Paragraph(body, P), Spacer(1, 6)]
    story += [Spacer(1, 20), Paragraph("Fait à Tunis. Signé électroniquement par les parties (format PAdES).", TAG)]
    doc.build(story)
    print("ok", fname)
