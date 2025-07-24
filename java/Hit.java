package com.Scanner.IPTV;

public class Hit {
    private String usuario;
    private String senha;
    private String painel;
    private String exp;
    private String activeCons;
    private String port;

    public Hit(String usuario, String senha, String painel, String exp, String activeCons, String port) {
        this.usuario = usuario;
        this.senha = senha;
        this.painel = painel;
        this.exp = exp;
        this.activeCons = activeCons;
        this.port = port;
    }

    public String getFormattedText() {
        String m3uUrl = "http://" + painel + "/get.php?username=" + usuario + "&password=" + senha + "&type=m3u_plus";
        return "🔧 By: APK Scanner IPTV\n\n" +
               "🌐 Servidor: http://" + painel + "\n" +
               "🚪 Porta: " + port + "\n" +
               "👤 Usuário: " + usuario + "\n" +
               "🔐 Senha: " + senha + "\n" +
               "⏳ Validade: " + (exp != null ? exp : "Ilimitado") + "\n" +
               "👁️‍🗨️ Conexões Permitidas: " + (activeCons != null ? activeCons : "?") + "\n" +
               "📥 Link M3U:\n" + m3uUrl + "\n\n" +
               "📲 Baixar APK Scanner IPTV:\n" +
               "🔗 https://github.com/deivid22srk/Scanner-IPTV/releases";
    }

    // Getters
    public String getUsuario() { return usuario; }
    public String getSenha() { return senha; }
    public String getPainel() { return painel; }
    public String getExp() { return exp; }
    public String getActiveCons() { return activeCons; }
    public String getPort() { return port; }
}

