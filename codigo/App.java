import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.stream.Collectors;

public class App {
    private static int[] tabelaDePaginas;
    private static int[] molduras;
    private static int tamPag;
    private static int tamMemVirtual;
    private static int tamMemFisica;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Leitura dos parâmetros de configuração
        System.out.println("Digite o tamanho da memória virtual (em bits, 2^n): ");
        tamMemVirtual = (int) Math.pow(2, scanner.nextInt());
        System.out.println("Digite o tamanho da memória física (em bits, 2^n): ");
        tamMemFisica = (int) Math.pow(2, scanner.nextInt());
        System.out.println("Digite o tamanho da página (em bits, 2^n): ");
        tamPag = (int) Math.pow(2, scanner.nextInt());

        scanner.close();

        tabelaDePaginas = new int[tamMemVirtual / tamPag];
        molduras = new int[tamMemFisica / tamPag];
        LinkedList<Integer> enderecosUsados = new LinkedList<>();
        Random random = new Random();
        List<Integer> enderecos = random.ints(0, tamMemVirtual).boxed().limit(tamMemVirtual + 300).collect(Collectors.toList());

        // Inicializa tabela de páginas e molduras
        populaTP();
        inicializaMolduras();

        // Alocar molduras conforme for recebendo os endereços
        for (int endereco : enderecos) {
            if (enderecosUsados.contains(endereco)) {
                continue;
            } else {
                enderecosUsados.add(endereco);
                int endFisico = achaEndFisico(endereco);

                if (endFisico < 0) {
                    System.out.println("Todas as molduras alocadas, parando programa.\n");
                    break;
                }

                System.out.println("Endereço virtual: " + endereco + " -> Endereço físico: " + endFisico);
            }
        }

        // Imprimir o conteúdo da tabela de páginas e da memória física
        imprimeTabelaDePaginas();
        imprimeMolduras();
    }

    public static int calculaEnderecoFisico(int endVirtual, int moldura) {
        int deslocamento = endVirtual % tamPag;
        return moldura * tamPag + deslocamento;
    }

    public static int encontraMoldura(int endVirtual) {
        int locTp = endVirtual / tamPag;

        if (tabelaDePaginas[locTp] != -1) {
            return tabelaDePaginas[locTp];
        }

        return alocaMoldura(locTp);
    }

    public static int alocaMoldura(int j) {
        for (int i = 0; i < molduras.length; i++) {
            if (molduras[i] == 0) {
                tabelaDePaginas[j] = i;
                molduras[i] = 1;
                return i;
            }
        }
        return -1;
    }

    public static int achaEndFisico(int endVirtual) {
        int moldura = encontraMoldura(endVirtual);
        if (moldura < 0) {
            return -1;
        }
        return calculaEnderecoFisico(endVirtual, moldura);
    }

    public static void populaTP() {
        for (int i = 0; i < tabelaDePaginas.length; i++) {
            tabelaDePaginas[i] = -1;
        }
    }

    public static void inicializaMolduras() {
        for (int i = 0; i < molduras.length; i++) {
            molduras[i] = 0;
        }
    }

    public static void imprimeTabelaDePaginas() {
        System.out.println("Tabela de Páginas:");

        for (int i = 0; i < tabelaDePaginas.length; i++) {
            System.out.println("Página " + i + ": Moldura " + tabelaDePaginas[i]);
        }

        System.out.println();
    }

    public static void imprimeMolduras() {
        System.out.println("Molduras da Memória Física:");

        for (int i = 0; i < molduras.length; i++) {
            System.out.println("Moldura " + i + ": " + (molduras[i] == 1 ? "Usada" : "Livre"));
        }
    }
}
