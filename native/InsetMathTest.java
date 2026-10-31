package com.caiograton.meubolso;

public class InsetMathTest {
    static int fails = 0;
    static void eq(String name, int got, int want) {
        if (got != want) { fails++; System.out.println("FALHOU  " + name + ": obtido " + got + ", esperado " + want); }
        else System.out.println("ok      " + name);
    }
    public static void main(String[] a) {
        int H = 2340;
        // sem teclado, navegação por gestos (63 px) com o app ocupando a tela toda
        eq("sem teclado, gestos", InsetMath.bottomPad(H, H, 63, 0), 63);
        // sem teclado, 3 botões (144 px)
        eq("sem teclado, 3 botoes", InsetMath.bottomPad(H, H, 144, 0), 144);
        // sistema já ajustou a janela acima da barra: nada a compensar
        eq("janela ja ajustada", InsetMath.bottomPad(H, H - 63, 63, 0), 0);
        // teclado aberto, janela NÃO encolhida: margem = altura do teclado
        eq("teclado, janela fixa", InsetMath.bottomPad(H, H, 63, 900), 900);
        // CASO DA FOTO: teclado aberto e janela encolhida pelo sistema: margem 0
        eq("teclado, janela encolhida", InsetMath.bottomPad(H, H - 900, 63, 900), 0);
        // encolheu só em parte
        eq("teclado, encolhida em parte", InsetMath.bottomPad(H, H - 700, 63, 900), 200);
        // teclado com 3 botões (o teclado já inclui a barra)
        eq("teclado 3 botoes, fixa", InsetMath.bottomPad(H, H, 144, 1000), 1000);
        eq("teclado 3 botoes, encolhida", InsetMath.bottomPad(H, H - 1000, 144, 1000), 0);
        // nunca negativo
        eq("sem negativos", InsetMath.bottomPad(H, H - 2000, 63, 900), 0);
        // topo
        eq("topo por baixo da barra", InsetMath.topPad(0, 100), 100);
        eq("topo ja abaixo da barra", InsetMath.topPad(100, 100), 0);
        eq("topo parcial", InsetMath.topPad(40, 100), 60);
        // faixa preta
        eq("faixa sem teclado", InsetMath.bottomBarHeight(false, 63), 63);
        eq("faixa com teclado", InsetMath.bottomBarHeight(true, 63), 0);
        // o espaço total nunca é contado duas vezes: altura útil = limite - topo
        int contentH = H - 900;                     // janela encolhida pelo teclado
        int usable = contentH - InsetMath.topPad(0, 100) - InsetMath.bottomPad(H, contentH, 63, 900);
        eq("area util com teclado (janela encolhida)", usable, 1440 - 100);
        contentH = H;                               // janela fixa
        usable = contentH - InsetMath.topPad(0, 100) - InsetMath.bottomPad(H, contentH, 63, 900);
        eq("area util com teclado (janela fixa)", usable, 1440 - 100);
        if (fails > 0) { System.out.println(fails + " teste(s) falharam"); System.exit(1); }
        System.out.println("Todos os testes passaram.");
    }
}
