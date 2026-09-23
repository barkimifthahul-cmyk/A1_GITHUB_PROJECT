# A1 Collector (optional server-side component)

Android can perform the public IDX fetch directly, but a server-side collector is recommended for reliable scheduled collection because websites can change and Android background execution is constrained.

This folder is reserved for a future FastAPI/Playwright collector. It must only access public/authorized sources and must not bypass authentication, CAPTCHA, paywalls, or access controls.
