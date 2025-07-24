// Versão alternativa do initViews() sem Toolbar
// USE APENAS se continuar dando erro de ActionBar

private void initViews() {
    // REMOVER ESTAS LINHAS se der erro:
    // toolbar = findViewById(R.id.toolbar);
    // setSupportActionBar(toolbar);
    
    panelsContainer = findViewById(R.id.panelsContainer);
    fileEditText = findViewById(R.id.fileEditText);
    proxyUrlEditText = findViewById(R.id.proxyUrlEditText);
    proxyFileEditText = findViewById(R.id.proxyFileEditText);
    speedSpinner = findViewById(R.id.speedSpinner);
    ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
            R.array.speed_options, android.R.layout.simple_spinner_item);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    speedSpinner.setAdapter(adapter);
    speedSpinner.setText(adapter.getItem(0), false);
    proxyRadioGroup = findViewById(R.id.proxyRadioGroup);
    proxyUrlInputLayout = findViewById(R.id.proxyUrlInputLayout);
    proxyFileInputLayout = findViewById(R.id.proxyFileInputLayout);
    startScanButton = findViewById(R.id.startScanButton);
    stopScanButton = findViewById(R.id.stopScanButton);
    copyAllButton = findViewById(R.id.copyAllButton);
    statusTextView = findViewById(R.id.statusTextView);
    hitsContainer = findViewById(R.id.hitsContainer);
    progressIndicator = findViewById(R.id.progressIndicator);
}