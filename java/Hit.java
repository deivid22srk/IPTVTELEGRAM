package com.mundodosbots.scanner;

public class Hit {
    private String usuario;
    private String senha;
    private String painel;
    private String exp;
    private String activeCons;
    private String m3uUrl;

    public Hit(String usuario, String senha, String painel, String exp, String activeCons) {
        this.usuario = usuario;
        this.senha = senha;
        this.painel = painel;
        this.exp = exp;
        this.activeCons = activeCons;
        this.m3uUrl = "http://" + painel + "/get.php?username=" + usuario + "&password=" + senha + "&type=m3u_plus";
    }

    public String getFormattedText() {
        return "🤖 HIT MUNDO DOS BOTS\n" +
               "🌐 Servidor: http://" + painel + "\n" +
               "🚪 Porta: 80\n" +
               "👤 Usuário: " + usuario + "\n" +
               "🔑 Senha: " + senha + "\n" +
               "📅 Expira: " + (exp != null ? exp : "Ilimitado") + "\n" +
               "👀 Conexões: " + (activeCons != null ? activeCons : "?") + "\n" +
               "📎 m3u_url: " + m3uUrl + "\n" +
               "🎯 FEITO PRA VOCÊ!";
    }

    // Getters
    public String getUsuario() { return usuario; }
    public String getSenha() { return senha; }
    public String getPainel() { return painel; }
    public String getExp() { return exp; }
    public String getActiveCons() { return activeCons; }
    public String getM3uUrl() { return m3uUrl; }
}

