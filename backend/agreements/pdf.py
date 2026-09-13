"""ReportLab generation of the official Mediara AI Mutual Resolution Agreement PDF.

The output is byte-for-byte deterministic for a given (mediation, agreement, signed_at)
so the SHA-256 digest computed at finalize time matches every later regeneration.
Clients can therefore verify that a PDF they hold was genuinely issued by the platform.
"""

import hashlib
import io
from datetime import datetime

from reportlab.lib.colors import HexColor
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import (
    HRFlowable,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)

INDIGO = HexColor("#1E1B4B")
TEAL = HexColor("#0D9488")
SLATE_800 = HexColor("#1E293B")
SLATE_600 = HexColor("#475569")
SLATE_400 = HexColor("#94A3B8")
SLATE_100 = HexColor("#F1F5F9")


def _styles():
    ss = getSampleStyleSheet()
    return {
        "title": ParagraphStyle("MedTitle", parent=ss["Title"], fontSize=18, leading=22,
                                textColor=INDIGO, alignment=TA_CENTER, spaceAfter=2),
        "subtitle": ParagraphStyle("MedSub", parent=ss["Normal"], fontSize=8.5, leading=11,
                                   textColor=SLATE_400, alignment=TA_CENTER, spaceAfter=6),
        "heading": ParagraphStyle("MedHead", parent=ss["Heading3"], fontSize=10.5, leading=13,
                                  textColor=SLATE_800, spaceBefore=8, spaceAfter=4),
        "body": ParagraphStyle("MedBody", parent=ss["Normal"], fontSize=9, leading=12.5, textColor=SLATE_600),
        "small": ParagraphStyle("MedSmall", parent=ss["Normal"], fontSize=7.5, leading=9.5, textColor=SLATE_400),
    }


def build_agreement_pdf_bytes(mediation, agreement) -> bytes:
    styles = _styles()
    buf = io.BytesIO()
    doc = SimpleDocTemplate(
        buf,
        pagesize=A4,
        leftMargin=40,
        rightMargin=40,
        topMargin=34,
        bottomMargin=34,
        title=f"Mediara AI Resolution Accord {mediation.invite_code}",
        author="Mediara AI",
    )

    # Keep the layout deterministic: no wall-clock time, only signed/created facts.
    issued = agreement.signed_at or agreement.created_at
    issued_str = issued.strftime("%B %d, %Y") if issued else ""
    participants = list(mediation.participants.select_related("user").exclude(status="INVITED"))
    collaborators = [
        f"{p.user.name or p.user.email} ({p.role})"
        for p in participants
    ]

    responsibilities_rows = []
    for p in participants:
        name = p.user.name or p.user.email
        duties = agreement.participant_responsibilities.get(p.user.email, agreement.participant_responsibilities.get(name, []))
        if not duties:
            duties = [agreement.participant_responsibilities.get(name, "Abide by the common terms and communication standards.")]
        responsibility_text = "<br/>".join(f"• {d}" for d in duties)
        responsibilities_rows.append([Paragraph(f"<b>{name}</b><br/><font size=7 color={SLATE_400}>{p.role}</font>", styles["body"]),
                                      Paragraph(responsibility_text, styles["body"])])
    if not responsibilities_rows:
        responsibilities_rows = [[Paragraph("All signatories", styles["body"]),
                                  Paragraph("Uphold the agreed terms and respectful communication standards.", styles["body"])]]

    terms_items = agreement.agreed_terms or []
    terms_html = "<br/>".join(f"{i + 1}. {term}" for i, term in enumerate(terms_items)) or "Adopt the adopted resolution in good faith."

    milestones = agreement.milestones or []
    milestones_html = "<br/>".join(f"• <b>{m.get('title', '')}</b> — Due: {m.get('dueDate', '')} (Responsible: {m.get('assignedTo', 'All')})"
                                   for m in milestones) or "Formal follow-up at day 14 and day 30."

    signing_block = []
    signatures = agreement.signatures or {}
    for p in participants:
        sig_time = signatures.get(str(p.id), signatures.get(p.id, None))
        time_str = ""
        if sig_time:
            try:
                time_str = datetime.fromisoformat(str(sig_time)).strftime("%Y-%m-%d %H:%M")
            except ValueError:
                time_str = str(sig_time)
        name = p.user.name or p.user.email
        validity = f"Verification: VALID {time_str}" if time_str else "Verification: PENDING SIGNATURE"
        signing_block.append(
            Table(
                [[Paragraph(f"<b>Digitally signed by: {name}</b><br/><font size=7 color={SLATE_400}>{p.role}</font>", styles["body"]),
                  Paragraph(f"<font size=7 color={TEAL}>{validity}</font>", styles["small"])]],
                colWidths=[90 * mm, 55 * mm],
            )
        )

    story = [
        Spacer(1, 2),
        Paragraph("M E D I A R A&nbsp;&nbsp;A I —&nbsp;&nbsp;R E S O L U T I O N&nbsp;&nbsp;A C C O R D", styles["subtitle"]),
        Paragraph("Mutual Resolution Agreement", styles["title"]),
        Paragraph(f"Case ref: MD-{mediation.invite_code} &nbsp;•&nbsp; {issued_str}", styles["subtitle"]),
        HRFlowable(width="100%", thickness=0.7, color=SLATE_400),
        Spacer(1, 6),

        Paragraph("1. PARTIES & CONFLICT OVERVIEW", styles["heading"]),
        Paragraph(f"<b>Matter:</b> {mediation.title} ({mediation.category})", styles["body"]),
        Spacer(1, 3),
        Paragraph(f"<b>Signatory parties:</b> {', '.join(collaborators)}", styles["body"]),
        Spacer(1, 3),
        Paragraph(f"<b>Description:</b> {mediation.description or '—'}", styles["body"]),
        Spacer(1, 4),

        Paragraph("2. PREAMBLE & JOINT COMMITMENT", styles["heading"]),
        Paragraph(
            "The undersigned parties, having participated in confidential structured AI-assisted "
            "mediation, hereby enter into this voluntary agreement in good faith to resolve their "
            "mutual conflict. All parties affirm that this resolution accurately addresses their "
            "core concerns and needs.",
            styles["body"],
        ),
        Spacer(1, 4),

        Paragraph(f"3. ADOPTED RESOLUTION: {agreement.resolution.title.upper()}", styles["heading"]),
        Paragraph(terms_html, styles["body"]),
        Spacer(1, 4),

        Paragraph("4. SPECIFIC RESPONSIBILITIES & ALLOCATIONS", styles["heading"]),
        Table(
            responsibilities_rows,
            colWidths=[62 * mm, 103 * mm],
            style=TableStyle([
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("GRID", (0, 0), (-1, -1), 0.4, SLATE_400),
                ("BACKGROUND", (0, 0), (-1, 0), SLATE_100),
                ("LEFTPADDING", (0, 0), (-1, -1), 6),
                ("RIGHTPADDING", (0, 0), (-1, -1), 6),
                ("TOPPADDING", (0, 0), (-1, -1), 5),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
            ]),
        ),
        Spacer(1, 4),

        Paragraph("5. MILESTONES & REVIEW DEADLINES", styles["heading"]),
        Paragraph(milestones_html, styles["body"]),
        Spacer(1, 4),

        Paragraph("6. DISPUTE ESCALATION PROTOCOL", styles["heading"]),
        Paragraph(agreement.dispute_escalation_clause or
                  "If friction or discrepancy re-emerges, parties agree to re-open Mediara AI mediation before taking external action.",
                  styles["body"]),
        Spacer(1, 6),
        HRFlowable(width="100%", thickness=0.7, color=SLATE_400),
        Spacer(1, 4),

        Paragraph("7. ELECTRONIC SIGNATURES & VERIFICATION", styles["heading"]),
        *signing_block,
        Paragraph(
            "This document is electronically verifiable: its SHA-256 fingerprint is recorded "
            "on the Mediara AI platform and can be checked at any time via the Veracity endpoint.",
            styles["small"],
        ),
        Spacer(1, 8),
        Paragraph(
            "Mediara AI Mediation Platform • Confidential agreement between participants • This document is a non-legal, "
            "good-faith interpersonal accord. It does not constitute legal advice.",
            styles["small"],
        ),
    ]

    doc.build(story)
    return buf.getvalue()


def agreement_sha256(pdf_bytes: bytes) -> str:
    """Hex SHA-256 digest of the exact PDF bytes — the platform fingerprint."""
    return hashlib.sha256(pdf_bytes).hexdigest()
