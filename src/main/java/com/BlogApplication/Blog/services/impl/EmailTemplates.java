package com.BlogApplication.Blog.services.impl;

// Shared HTML shell for account emails (verification, password reset) - same ocean gradient
// and leaf-wave mark as authPage.css, translated into email-safe markup: table layout,
// inline styles, bgcolor fallback for Outlook's Word rendering engine (which ignores CSS
// gradients), and a @keyframes wobble that's progressive enhancement only - clients that
// strip <style> animations just show the mark still, which degrades cleanly either way.
final class EmailTemplates {

    private EmailTemplates() {
    }

    static String render(String eyebrow, String headline, String bodyHtml, String ctaText, String ctaUrl, String expiryNote, String logoUrl) {
        return SHELL
                .replace("{{SUBJECT}}", eyebrow)
                .replace("{{EYEBROW}}", eyebrow)
                .replace("{{HEADLINE}}", headline)
                .replace("{{BODY}}", bodyHtml)
                .replace("{{CTA_TEXT}}", ctaText)
                .replace("{{CTA_URL}}", ctaUrl)
                .replace("{{EXPIRY_NOTE}}", expiryNote)
                .replace("{{LOGO_URL}}", logoUrl);
    }

    private static final String SHELL = """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <meta name="color-scheme" content="light">
              <meta name="supported-color-schemes" content="light">
              <title>{{SUBJECT}}</title>
              <style>
                @media (prefers-reduced-motion: reduce) { .brand-mark { animation: none !important; } }
                .brand-mark { animation: bhs-vibrate 0.5s ease-in-out infinite; }
                @keyframes bhs-vibrate {
                  0%,100% { transform:translate(0,0); }
                  20% { transform:translate(-1.5px,1px); }
                  40% { transform:translate(1.5px,-1px); }
                  60% { transform:translate(-1.5px,-1px); }
                  80% { transform:translate(1.5px,1px); }
                }
              </style>
            </head>
            <body style="margin:0;padding:0;background:#eef3fb;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#eef3fb;padding:32px 16px;">
                <tr><td align="center">
                  <table role="presentation" width="520" cellpadding="0" cellspacing="0" style="width:100%;max-width:520px;border-collapse:collapse;">
                    <tr><td bgcolor="#14428c" style="background:#14428c;background-image:linear-gradient(160deg,#0f2a5c 0%,#14428c 42%,#2f66d1 78%,#5b8def 100%);padding:38px 24px 30px;text-align:center;border-radius:16px 16px 0 0;">
                      <span class="brand-mark" style="display:inline-block;">
                        <img src="{{LOGO_URL}}" width="52" height="52" alt="Bodh Sea" style="display:block;border:0;outline:none;">
                      </span>
                      <span style="display:block;margin-top:12px;color:#ffffff;font-size:20px;font-weight:700;letter-spacing:0.02em;">Bodh Sea</span>
                      <span style="display:block;margin-top:3px;color:rgba(255,255,255,0.72);font-size:12.5px;letter-spacing:0.03em;">Set your thoughts adrift</span>
                    </td></tr>
                    <tr><td bgcolor="#ffffff" style="background:#ffffff;padding:34px 30px 30px;border-radius:0 0 16px 16px;">
                      <p style="font-size:11.5px;font-weight:700;letter-spacing:0.08em;text-transform:uppercase;color:#5b8def;margin:0 0 10px;">{{EYEBROW}}</p>
                      <p style="font-size:21px;font-weight:700;color:#14243d;margin:0 0 14px;line-height:1.3;">{{HEADLINE}}</p>
                      <p style="font-size:15px;line-height:1.65;color:#3a4557;margin:0 0 26px;">{{BODY}}</p>
                      <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 auto 26px;border-collapse:collapse;"><tr><td>
                        <a href="{{CTA_URL}}" style="background:#2f66d1;background-image:linear-gradient(135deg,#2f66d1 0%,#14428c 100%);color:#ffffff;display:inline-block;padding:13px 34px;border-radius:10px;font-size:15px;font-weight:700;text-decoration:none;letter-spacing:0.01em;">{{CTA_TEXT}}</a>
                      </td></tr></table>
                      <p style="font-size:12.5px;line-height:1.6;color:#8b93a3;margin:0 0 4px;word-break:break-all;">Or paste this link into your browser:<br><a href="{{CTA_URL}}" style="color:#2f66d1;text-decoration:underline;">{{CTA_URL}}</a></p>
                      <p style="font-size:12.5px;color:#8b93a3;margin:18px 0 0;padding-top:16px;border-top:1px solid #eef1f6;">{{EXPIRY_NOTE}}</p>
                    </td></tr>
                  </table>
                  <p style="text-align:center;font-size:11.5px;color:#9aa3b2;margin:18px 0 0;letter-spacing:0.02em;">Bodh Sea &middot; Thoughts, adrift and ashore</p>
                </td></tr>
              </table>
            </body>
            </html>
            """;
}
