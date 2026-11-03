package com.arkael.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Konfigurasi AI ala ai-agent (github.com/Arkael-Dev/ai-agent):
 * provider -> endpoint, model, dan key per provider. Key diisi manual pengguna.
 */
final class AiStore {
    private static final String P = "arkael_ai";

    /* Preset provider, sama dengan ai-agent-chat.sh */
    static final String[] PROVIDERS = {
        "deepseek", "openai", "gemini", "claude", "openrouter", "groq", "ollama", "custom"
    };

    static String label(String p) {
        switch (p) {
            case "deepseek":   return "DeepSeek";
            case "openai":     return "OpenAI";
            case "gemini":     return "Gemini";
            case "claude":     return "Claude";
            case "openrouter": return "OpenRouter";
            case "groq":       return "Groq";
            case "ollama":     return "Ollama (lokal)";
            default:           return "Custom AI";
        }
    }

    /** Endpoint chat/completions penuh. */
    static String url(String p, String customUrl) {
        switch (p) {
            case "deepseek":   return "https://api.deepseek.com/chat/completions";
            case "openai":     return "https://api.openai.com/v1/chat/completions";
            case "gemini":     return "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
            case "claude":     return "https://api.anthropic.com/v1/chat/completions";
            case "openrouter": return "https://openrouter.ai/api/v1/chat/completions";
            case "groq":       return "https://api.groq.com/openai/v1/chat/completions";
            case "ollama":     return "http://localhost:11434/v1/chat/completions";
            default:           return customUrl == null ? "" : customUrl;
        }
    }

    /** Model default per provider (boleh ditimpa). */
    static String defModel(String p) {
        switch (p) {
            case "deepseek":   return "deepseek-flash";
            case "openai":     return "gpt-4o-mini";
            case "gemini":     return "gemini-2.5-flash";
            case "claude":     return "claude-sonnet-5-5";
            case "openrouter": return "openrouter/auto";
            case "groq":       return "llama-3.3-70b-versatile";
            case "ollama":     return "qwen2.5:3b";
            default:           return "";
        }
    }

    /** Ollama tidak butuh key. */
    static boolean needsKey(String p) { return !"ollama".equals(p); }

    /**
     * Nama field batas token, sama seperti TOKEN_FIELD di ai-agent-chat.sh:
     * model OpenAI terbaru menolak max_tokens dan hanya menerima max_completion_tokens.
     */
    static String tokenField(String p) {
        return "openai".equals(p) ? "max_completion_tokens" : "max_tokens";
    }

    static int maxTokens(Context c) {
        int v = sp(c).getInt("max_tokens", 8000);
        return v <= 0 ? 8000 : v;
    }

    static void setMaxTokens(Context c, int v) {
        sp(c).edit().putInt("max_tokens", v <= 0 ? 8000 : v).apply();
    }

    /** "key …938b" untuk ditampilkan; key utuh tidak pernah dicetak. */
    static String keyMask(String k) {
        if (k == null || k.isEmpty()) return "belum ada";
        if (k.length() <= 8) return "\u2026" + k;
        return k.substring(0, 3) + "\u2026" + k.substring(k.length() - 4);
    }

    private AiStore() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(P, Context.MODE_PRIVATE);
    }

    static String provider(Context c) {
        String p = sp(c).getString("provider", "deepseek");
        for (String k : PROVIDERS) if (k.equals(p)) return p;
        return "deepseek";
    }

    static String customUrl(Context c) { return sp(c).getString("custom_url", ""); }
    static String key(Context c)       { return sp(c).getString("key_" + provider(c), ""); }
    static String key(Context c, String p) { return sp(c).getString("key_" + p, ""); }

    static String model(Context c) {
        String p = provider(c);
        String m = sp(c).getString("model_" + p, "");
        return m.isEmpty() ? defModel(p) : m;
    }

    static String model(Context c, String p) {
        String m = sp(c).getString("model_" + p, "");
        return m.isEmpty() ? defModel(p) : m;
    }

    static boolean agent(Context c)      { return sp(c).getBoolean("agent", false); }

    /* --- Git: repo tujuan push, dipakai tool git_* dan menu Git --- */
    static String gitOwner(Context c)  { return sp(c).getString("git_owner", "Arkael-Dev"); }
    static String gitRepo(Context c)   { return sp(c).getString("git_repo", "ai-agent"); }
    static String gitBranch(Context c) {
        String b = sp(c).getString("git_branch", AiGit.DEFAULT_BRANCH);
        return b == null || b.trim().isEmpty() ? AiGit.DEFAULT_BRANCH : b.trim();
    }
    static String gitUser(Context c)   { return sp(c).getString("git_user", "arkael-agent"); }
    static String gitEmail(Context c)  { return sp(c).getString("git_email", "agent@arkael.local"); }
    static String gitToken(Context c)  { return sp(c).getString("git_token", ""); }
    /** Token hanya ditampilkan samar; token utuh tak pernah dicetak. */
    static String gitTokenMask(Context c) { return keyMask(gitToken(c)); }

    static void setGitOwner(Context c, String v)  { sp(c).edit().putString("git_owner", trim(v, "Arkael-Dev")).apply(); }
    static void setGitRepo(Context c, String v)   { sp(c).edit().putString("git_repo", trim(v, "ai-agent")).apply(); }
    static void setGitBranch(Context c, String v) { sp(c).edit().putString("git_branch", trim(v, AiGit.DEFAULT_BRANCH)).apply(); }
    static void setGitUser(Context c, String v)   { sp(c).edit().putString("git_user", trim(v, "arkael-agent")).apply(); }
    static void setGitEmail(Context c, String v)  { sp(c).edit().putString("git_email", trim(v, "agent@arkael.local")).apply(); }
    static void setGitToken(Context c, String v)  { sp(c).edit().putString("git_token", trim(v, "")).apply(); }

    private static String trim(String v, String def) {
        String s = v == null ? "" : v.trim();
        return s.isEmpty() ? def : s;
    }
    static boolean confirm(Context c)    { return sp(c).getBoolean("confirm", true); }
    static int maxIter(Context c)        { return sp(c).getInt("max_iters", 25); }

    static void setProvider(Context c, String p) { sp(c).edit().putString("provider", p).apply(); }
    static void setKey(Context c, String p, String v) {
        sp(c).edit().putString("key_" + p, v == null ? "" : v.trim()).apply();
    }
    static void setModel(Context c, String p, String v) {
        sp(c).edit().putString("model_" + p, v == null ? "" : v.trim()).apply();
    }
    static void setCustomUrl(Context c, String v) {
        sp(c).edit().putString("custom_url", v == null ? "" : v.trim()).apply();
    }
    static void setAgent(Context c, boolean v)   { sp(c).edit().putBoolean("agent", v).apply(); }
    static void setConfirm(Context c, boolean v) { sp(c).edit().putBoolean("confirm", v).apply(); }

    /** Kenapa belum siap, atau "" kalau siap. */
    static String problem(Context c) {
        String p = provider(c);
        if (!needsKey(p)) return "";
        if (key(c).isEmpty()) return "API key " + label(p) + " belum diisi";
        return "";
    }

    /** Siap dipakai: provider butuh key dan key sudah ada. */
    static boolean ready(Context c) {
        String p = provider(c);
        if (!needsKey(p)) return true;
        return !key(c).isEmpty();
    }
}
