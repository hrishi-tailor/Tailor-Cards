// Apply the visitor's saved theme before the first paint (no flash). A file rather than an inline script so the
// Content-Security-Policy can forbid inline scripts.
try {
  var t = localStorage.getItem('tc-theme');
  if (t === 'light' || t === 'dark') document.documentElement.setAttribute('data-theme', t);
} catch (e) {}
