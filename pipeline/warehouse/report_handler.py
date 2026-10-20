"""Lambda entry point: the Monday email. Reads last week's numbers from the warehouse views, emails them through
SES and keeps a copy in the data lake (reports/weekly/<week>.html)."""

from __future__ import annotations

import logging
import os
from datetime import datetime, timezone
from zoneinfo import ZoneInfo

from . import report
from .athena import Athena

log = logging.getLogger()
log.setLevel(logging.INFO)

STORE_ZONE = ZoneInfo("America/Toronto")


def handler(event, context, *, athena=None, ses=None, s3=None, today=None):
    import boto3  # provided by the Lambda runtime

    env = os.environ
    athena = athena or Athena(boto3.client("athena"), env["ATHENA_WORKGROUP"], env["GLUE_DATABASE"])
    ses = ses or boto3.client("sesv2")
    s3 = s3 or boto3.client("s3")
    today = today or datetime.now(timezone.utc).astimezone(STORE_ZONE).date()
    week_start = report.last_week(today)

    subject, html_body, text_body = report.render(report.gather(athena.rows, week_start))
    s3.put_object(Bucket=env["DATA_BUCKET"], Key=f"reports/weekly/{week_start.isoformat()}.html",
                  Body=html_body.encode("utf-8"), ContentType="text/html; charset=utf-8")
    recipients = [r.strip() for r in env["REPORT_RECIPIENTS"].split(",") if r.strip()]
    message_id = report.send(ses, env["REPORT_FROM"], recipients, subject, html_body, text_body)
    log.info("Sent the report for the week of %s to %d recipients (%s)", week_start, len(recipients), message_id)
    return {"week_start": week_start.isoformat(), "subject": subject, "message_id": message_id}
