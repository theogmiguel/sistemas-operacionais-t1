import java.util.*;

// PUCRS - Escola Politécnica - Sistemas Operacionais
// Prof. Fernando Dotti
// Código mesclado com gerenciamento de memória virtual

public class Sistema {

    // Variáveis de configuração para memória virtual
    private int tamMemVirtual;    // Tamanho da memória virtual (em palavras)
    private int tamMemFisica;     // Tamanho da memória física (em palavras)
    private int tamPag;           // Tamanho da página (em palavras)
    private int[] tabelaDePaginas;// Tabela de páginas: mapeia páginas virtuais para molduras físicas
    private int[] molduras;       // Estado das molduras: 0 = livre, 1 = ocupada

    // -------------------------------------------------------------------------------------------------------
    // --------------------- H A R D W A R E - definições de HW ----------------------------------------------

    public class Memory {
        public Word[][] molduras; // Memória física dividida em molduras, cada uma com tamPag palavras
        public int numMolduras;

        public Memory(int tamMemFisica, int tamPag) {
            this.numMolduras = tamMemFisica / tamPag;
            molduras = new Word[numMolduras][tamPag];
            for (int i = 0; i < numMolduras; i++) {
                for (int j = 0; j < tamPag; j++) {
                    molduras[i][j] = new Word(Opcode.___, -1, -1, -1);
                }
            }
        }
    }

    public class Word {
        public Opcode opc;
        public int ra;
        public int rb;
        public int p;

        public Word(Opcode _opc, int _ra, int _rb, int _p) {
            opc = _opc;
            ra = _ra;
            rb = _rb;
            p = _p;
        }
    }

    public enum Opcode {
        DATA, ___, JMP, JMPI, JMPIG, JMPIL, JMPIE, JMPIM, JMPIGM, JMPILM, JMPIEM,
        JMPIGK, JMPILK, JMPIEK, JMPIGT, ADDI, SUBI, ADD, SUB, MULT,
        LDI, LDD, STD, LDX, STX, MOVE, SYSCALL, STOP
    }

    public enum Interrupts {
        noInterrupt, intEnderecoInvalido, intInstrucaoInvalida, intOverflow, intSTOP
    }

    public class CPU {
        private int maxInt = 32767;
        private int minInt = -32767;
        private int pc;
        private Word ir;
        private int[] reg = new int[10];
        private Interrupts irpt;
        private Memory m;
        private InterruptHandling ih;
        private SysCallHandling sysCall;
        private boolean cpuStop;
        private boolean debug;
        private Utilities u;

        public CPU(Memory _mem, boolean _debug) {
            m = _mem;
            debug = _debug;
        }

        public void setAddressOfHandlers(InterruptHandling _ih, SysCallHandling _sysCall) {
            ih = _ih;
            sysCall = _sysCall;
        }

        public void setUtilities(Utilities _u) {
            u = _u;
        }

        // Traduz endereço virtual para físico
        private int traduzEndereco(int endVirtual) {
            int pagina = endVirtual / tamPag;
            int deslocamento = endVirtual % tamPag;
            if (pagina >= tabelaDePaginas.length) {
                irpt = Interrupts.intEnderecoInvalido;
                return -1;
            }
            if (tabelaDePaginas[pagina] == -1) { // Falha de página
                int moldura = alocaMoldura(pagina);
                if (moldura == -1) {
                    irpt = Interrupts.intEnderecoInvalido; // Sem molduras livres
                    return -1;
                }
            }
            int moldura = tabelaDePaginas[pagina];
            int endFisico = moldura * tamPag + deslocamento;
            if (debug) {
                System.out.println("Traduzindo endereço virtual " + endVirtual + " para físico " + endFisico);
            }
            return endFisico;
        }

        // Aloca uma moldura livre
        private int alocaMoldura(int pagina) {
            for (int i = 0; i < molduras.length; i++) {
                if (molduras[i] == 0) {
                    tabelaDePaginas[pagina] = i;
                    molduras[i] = 1;
                    return i;
                }
            }
            return -1; // Nenhuma moldura livre
        }

        private boolean legal(int e) {
            int endFisico = traduzEndereco(e);
            if (endFisico >= 0 && endFisico < tamMemFisica) {
                return true;
            } else {
                irpt = Interrupts.intEnderecoInvalido;
                return false;
            }
        }

        private boolean testOverflow(int v) {
            if (v < minInt || v > maxInt) {
                irpt = Interrupts.intOverflow;
                return false;
            }
            return true;
        }

        public void setContext(int _pc) {
            pc = _pc;
            irpt = Interrupts.noInterrupt;
        }

        public void run() {
            cpuStop = false;
            while (!cpuStop) {
                if (legal(pc)) {
                    int pcFisico = traduzEndereco(pc);
                    ir = m.molduras[pcFisico / tamPag][pcFisico % tamPag];
                    if (debug) {
                        System.out.print("                                              regs: ");
                        for (int i = 0; i < 10; i++) {
                            System.out.print(" r[" + i + "]:" + reg[i]);
                        }
                        System.out.println();
                        System.out.print("                      pc: " + pc + "       exec: ");
                        u.dump(ir);
                    }

                    switch (ir.opc) {
                        case LDI:
                            reg[ir.ra] = ir.p;
                            pc++;
                            break;
                        case LDD:
                            int endFisicoLDD = traduzEndereco(ir.p);
                            if (endFisicoLDD != -1) {
                                reg[ir.ra] = m.molduras[endFisicoLDD / tamPag][endFisicoLDD % tamPag].p;
                                pc++;
                            }
                            break;
                        case LDX:
                            int endFisicoLDX = traduzEndereco(reg[ir.rb]);
                            if (endFisicoLDX != -1) {
                                reg[ir.ra] = m.molduras[endFisicoLDX / tamPag][endFisicoLDX % tamPag].p;
                                pc++;
                            }
                            break;
                        case STD:
                            int endFisicoSTD = traduzEndereco(ir.p);
                            if (endFisicoSTD != -1) {
                                m.molduras[endFisicoSTD / tamPag][endFisicoSTD % tamPag].opc = Opcode.DATA;
                                m.molduras[endFisicoSTD / tamPag][endFisicoSTD % tamPag].p = reg[ir.ra];
                                pc++;
                                if (debug) {
                                    System.out.print("                                  ");
                                    u.dump(ir.p, ir.p + 1);
                                }
                            }
                            break;
                        case STX:
                            int endFisicoSTX = traduzEndereco(reg[ir.ra]);
                            if (endFisicoSTX != -1) {
                                m.molduras[endFisicoSTX / tamPag][endFisicoSTX % tamPag].opc = Opcode.DATA;
                                m.molduras[endFisicoSTX / tamPag][endFisicoSTX % tamPag].p = reg[ir.rb];
                                pc++;
                            }
                            break;
                        case MOVE:
                            reg[ir.ra] = reg[ir.rb];
                            pc++;
                            break;
                        case ADD:
                            reg[ir.ra] = reg[ir.ra] + reg[ir.rb];
                            testOverflow(reg[ir.ra]);
                            pc++;
                            break;
                        case ADDI:
                            reg[ir.ra] = reg[ir.ra] + ir.p;
                            testOverflow(reg[ir.ra]);
                            pc++;
                            break;
                        case SUB:
                            reg[ir.ra] = reg[ir.ra] - reg[ir.rb];
                            testOverflow(reg[ir.ra]);
                            pc++;
                            break;
                        case SUBI:
                            reg[ir.ra] = reg[ir.ra] - ir.p;
                            testOverflow(reg[ir.ra]);
                            pc++;
                            break;
                        case MULT:
                            reg[ir.ra] = reg[ir.ra] * reg[ir.rb];
                            testOverflow(reg[ir.ra]);
                            pc++;
                            break;
                        case JMP:
                            pc = ir.p;
                            break;
                        case JMPIM:
                            int endFisicoJMPIM = traduzEndereco(ir.p);
                            if (endFisicoJMPIM != -1) {
                                pc = m.molduras[endFisicoJMPIM / tamPag][endFisicoJMPIM % tamPag].p;
                            }
                            break;
                        case JMPIG:
                            if (reg[ir.rb] > 0) {
                                pc = reg[ir.ra];
                            } else {
                                pc++;
                            }
                            break;
                        case JMPIGK:
                            if (reg[ir.rb] > 0) {
                                pc = ir.p;
                            } else {
                                pc++;
                            }
                            break;
                        case JMPILK:
                            if (reg[ir.rb] < 0) {
                                pc = ir.p;
                            } else {
                                pc++;
                            }
                            break;
                        case JMPIEK:
                            if (reg[ir.rb] == 0) {
                                pc = ir.p;
                            } else {
                                pc++;
                            }
                            break;
                        case JMPIL:
                            if (reg[ir.rb] < 0) {
                                pc = reg[ir.ra];
                            } else {
                                pc++;
                            }
                            break;
                        case JMPIE:
                            if (reg[ir.rb] == 0) {
                                pc = reg[ir.ra];
                            } else {
                                pc++;
                            }
                            break;
                        case JMPIGM:
                            int endFisicoJMPIGM = traduzEndereco(ir.p);
                            if (endFisicoJMPIGM != -1) {
                                if (reg[ir.rb] > 0) {
                                    pc = m.molduras[endFisicoJMPIGM / tamPag][endFisicoJMPIGM % tamPag].p;
                                } else {
                                    pc++;
                                }
                            }
                            break;
                        case JMPILM:
                            int endFisicoJMPILM = traduzEndereco(ir.p);
                            if (endFisicoJMPILM != -1) {
                                if (reg[ir.rb] < 0) {
                                    pc = m.molduras[endFisicoJMPILM / tamPag][endFisicoJMPILM % tamPag].p;
                                } else {
                                    pc++;
                                }
                            }
                            break;
                        case JMPIEM:
                            int endFisicoJMPIEM = traduzEndereco(ir.p);
                            if (endFisicoJMPIEM != -1) {
                                if (reg[ir.rb] == 0) {
                                    pc = m.molduras[endFisicoJMPIEM / tamPag][endFisicoJMPIEM % tamPag].p;
                                } else {
                                    pc++;
                                }
                            }
                            break;
                        case JMPIGT:
                            if (reg[ir.ra] > reg[ir.rb]) {
                                pc = ir.p;
                            } else {
                                pc++;
                            }
                            break;
                        case DATA:
                            irpt = Interrupts.intInstrucaoInvalida;
                            break;
                        case SYSCALL:
                            sysCall.handle();
                            pc++;
                            break;
                        case STOP:
                            sysCall.stop();
                            cpuStop = true;
                            break;
                        default:
                            irpt = Interrupts.intInstrucaoInvalida;
                            break;
                    }
                }
                if (irpt != Interrupts.noInterrupt) {
                    ih.handle(irpt);
                    cpuStop = true;
                }
            }
        }
    }

    public class HW {
        public Memory mem;
        public CPU cpu;

        public HW(int tamMemFisica, int tamPag) {
            mem = new Memory(tamMemFisica, tamPag);
            cpu = new CPU(mem, true); // Debug ativado
        }
    }

    // -------------------------------------------------------------------------------------------------------
    // --------------------- S O F T W A R E - Sistema Operacional -------------------------------------------

    public class InterruptHandling {
        private HW hw;

        public InterruptHandling(HW _hw) {
            hw = _hw;
        }

        public void handle(Interrupts irpt) {
            System.out.println("                                               Interrupcao " + irpt + "   pc: " + hw.cpu.pc);
        }
    }

    public class SysCallHandling {
        private HW hw;

        public SysCallHandling(HW _hw) {
            hw = _hw;
        }

        public void stop() {
            System.out.println("                                               SYSCALL STOP");
        }

        public void handle() {
            System.out.println("SYSCALL pars:  " + hw.cpu.reg[8] + " / " + hw.cpu.reg[9]);
            if (hw.cpu.reg[8] == 1) {
                // Leitura não implementada
            } else if (hw.cpu.reg[8] == 2) {
                int endFisico = hw.cpu.traduzEndereco(hw.cpu.reg[9]);
                if (endFisico != -1) {
                    System.out.println("OUT:   " + hw.mem.molduras[endFisico / tamPag][endFisico % tamPag].p);
                }
            } else {
                System.out.println("  PARAMETRO INVALIDO");
            }
        }
    }

    public class Utilities {
        private HW hw;

        public Utilities(HW _hw) {
            hw = _hw;
        }

        private void loadProgram(Word[] p) {
            // Carrega o programa na memória virtual (posições 0 em diante)
            for (int i = 0; i < p.length; i++) {
                int endFisico = hw.cpu.traduzEndereco(i);
                if (endFisico != -1) {
                    int moldura = endFisico / tamPag;
                    int deslocamento = endFisico % tamPag;
                    hw.mem.molduras[moldura][deslocamento].opc = p[i].opc;
                    hw.mem.molduras[moldura][deslocamento].ra = p[i].ra;
                    hw.mem.molduras[moldura][deslocamento].rb = p[i].rb;
                    hw.mem.molduras[moldura][deslocamento].p = p[i].p;
                } else {
                    System.out.println("Erro ao carregar programa: memória insuficiente");
                    break;
                }
            }
        }

        public void dump(Word w) {
            System.out.print("[ ");
            System.out.print(w.opc);
            System.out.print(", ");
            System.out.print(w.ra);
            System.out.print(", ");
            System.out.print(w.rb);
            System.out.print(", ");
            System.out.print(w.p);
            System.out.println("  ] ");
        }

        public void dump(int ini, int fim) {
            for (int i = ini; i < fim; i++) {
                int endFisico = hw.cpu.traduzEndereco(i);
                if (endFisico != -1) {
                    int moldura = endFisico / tamPag;
                    int deslocamento = endFisico % tamPag;
                    System.out.print(i + ":  ");
                    dump(hw.mem.molduras[moldura][deslocamento]);
                }
            }
        }

        private void loadAndExec(Word[] p) {
            loadProgram(p);
            System.out.println("---------------------------------- programa carregado na memoria");
            dump(0, p.length);
            hw.cpu.setContext(0);
            System.out.println("---------------------------------- inicia execucao ");
            hw.cpu.run();
            System.out.println("---------------------------------- memoria após execucao ");
            dump(0, p.length);
        }
    }

    public class SO {
        public InterruptHandling ih;
        public SysCallHandling sc;
        public Utilities utils;

        public SO(HW hw) {
            ih = new InterruptHandling(hw);
            sc = new SysCallHandling(hw);
            hw.cpu.setAddressOfHandlers(ih, sc);
            utils = new Utilities(hw);
        }
    }

    // -------------------------------------------------------------------------------------------------------
    // ------------------- S I S T E M A --------------------------------------------------------------------

    public HW hw;
    public SO so;
    public Programs progs;

    public Sistema(int tamMemVirtual, int tamMemFisica, int tamPag) {
        this.tamMemVirtual = tamMemVirtual;
        this.tamMemFisica = tamMemFisica;
        this.tamPag = tamPag;
        tabelaDePaginas = new int[tamMemVirtual / tamPag];
        molduras = new int[tamMemFisica / tamPag];
        for (int i = 0; i < tabelaDePaginas.length; i++) {
            tabelaDePaginas[i] = -1; // Inicializa tabela de páginas
        }
        for (int i = 0; i < molduras.length; i++) {
            molduras[i] = 0; // Inicializa molduras como livres
        }
        hw = new HW(tamMemFisica, tamPag);
        so = new SO(hw);
        hw.cpu.setUtilities(so.utils);
        progs = new Programs();
    }

    public void run() {
        so.utils.loadAndExec(progs.retrieveProgram("fatorialV2"));
    }

    // -------------------------------------------------------------------------------------------------------
    // ------------------- Instancia e testa sistema --------------------------------------------------------

    public static void main(String args[]) {
        // Configuração fixa para teste (pode ser ajustada ou lida do usuário)
        Sistema s = new Sistema(2048, 1024, 4); // Memória virtual: 2048 palavras, física: 1024 palavras, página: 4 palavras
        s.run();
    }

    // -------------------------------------------------------------------------------------------------------
    // ------------------- P R O G R A M A S ----------------------------------------------------------------

    public class Program {
        public String name;
        public Word[] image;

        public Program(String n, Word[] i) {
            name = n;
            image = i;
        }
    }

    public class Programs {
        public Word[] retrieveProgram(String pname) {
            for (Program p : progs) {
                if (p != null && p.name.equals(pname)) return p.image;
            }
            return null;
        }

        public Program[] progs = {
            new Program("fatorial", new Word[] {
                new Word(Opcode.LDI, 0, -1, 7),
                new Word(Opcode.LDI, 1, -1, 1),
                new Word(Opcode.LDI, 6, -1, 1),
                new Word(Opcode.LDI, 7, -1, 8),
                new Word(Opcode.JMPIE, 7, 0, 0),
                new Word(Opcode.MULT, 1, 0, -1),
                new Word(Opcode.SUB, 0, 6, -1),
                new Word(Opcode.JMP, -1, -1, 4),
                new Word(Opcode.STD, 1, -1, 10),
                new Word(Opcode.STOP, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1)
            }),
            new Program("fatorialV2", new Word[] {
                new Word(Opcode.LDI, 0, -1, 5),
                new Word(Opcode.STD, 0, -1, 19),
                new Word(Opcode.LDD, 0, -1, 19),
                new Word(Opcode.LDI, 1, -1, -1),
                new Word(Opcode.LDI, 2, -1, 13),
                new Word(Opcode.JMPIL, 2, 0, -1),
                new Word(Opcode.LDI, 1, -1, 1),
                new Word(Opcode.LDI, 6, -1, 1),
                new Word(Opcode.LDI, 7, -1, 13),
                new Word(Opcode.JMPIE, 7, 0, 0),
                new Word(Opcode.MULT, 1, 0, -1),
                new Word(Opcode.SUB, 0, 6, -1),
                new Word(Opcode.JMP, -1, -1, 9),
                new Word(Opcode.STD, 1, -1, 18),
                new Word(Opcode.LDI, 8, -1, 2),
                new Word(Opcode.LDI, 9, -1, 18),
                new Word(Opcode.SYSCALL, -1, -1, -1),
                new Word(Opcode.STOP, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1)
            }),
            new Program("progMinimo", new Word[] {
                new Word(Opcode.LDI, 0, -1, 999),
                new Word(Opcode.STD, 0, -1, 8),
                new Word(Opcode.STD, 0, -1, 9),
                new Word(Opcode.STD, 0, -1, 10),
                new Word(Opcode.STD, 0, -1, 11),
                new Word(Opcode.STD, 0, -1, 12),
                new Word(Opcode.STOP, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1)
            }),
            new Program("fibonacci10", new Word[] {
                new Word(Opcode.LDI, 1, -1, 0),
                new Word(Opcode.STD, 1, -1, 20),
                new Word(Opcode.LDI, 2, -1, 1),
                new Word(Opcode.STD, 2, -1, 21),
                new Word(Opcode.LDI, 0, -1, 22),
                new Word(Opcode.LDI, 6, -1, 6),
                new Word(Opcode.LDI, 7, -1, 31),
                new Word(Opcode.LDI, 3, -1, 0),
                new Word(Opcode.ADD, 3, 1, -1),
                new Word(Opcode.LDI, 1, -1, 0),
                new Word(Opcode.ADD, 1, 2, -1),
                new Word(Opcode.ADD, 2, 3, -1),
                new Word(Opcode.STX, 0, 2, -1),
                new Word(Opcode.ADDI, 0, -1, 1),
                new Word(Opcode.SUB, 7, 0, -1),
                new Word(Opcode.JMPIG, 6, 7, -1),
                new Word(Opcode.STOP, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1)
            }),
            new Program("fibonacci10v2", new Word[] {
                new Word(Opcode.LDI, 1, -1, 0),
                new Word(Opcode.STD, 1, -1, 20),
                new Word(Opcode.LDI, 2, -1, 1),
                new Word(Opcode.STD, 2, -1, 21),
                new Word(Opcode.LDI, 0, -1, 22),
                new Word(Opcode.LDI, 6, -1, 6),
                new Word(Opcode.LDI, 7, -1, 31),
                new Word(Opcode.MOVE, 3, 1, -1),
                new Word(Opcode.MOVE, 1, 2, -1),
                new Word(Opcode.ADD, 2, 3, -1),
                new Word(Opcode.STX, 0, 2, -1),
                new Word(Opcode.ADDI, 0, -1, 1),
                new Word(Opcode.SUB, 7, 0, -1),
                new Word(Opcode.JMPIG, 6, 7, -1),
                new Word(Opcode.STOP, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1)
            }),
            new Program("fibonacciREAD", new Word[] {
                new Word(Opcode.LDI, 8, -1, 1),
                new Word(Opcode.LDI, 9, -1, 55),
                new Word(Opcode.SYSCALL, -1, -1, -1),
                new Word(Opcode.LDD, 7, -1, 55),
                new Word(Opcode.LDI, 3, -1, 0),
                new Word(Opcode.ADD, 3, 7, -1),
                new Word(Opcode.LDI, 4, -1, 36),
                new Word(Opcode.LDI, 1, -1, -1),
                new Word(Opcode.STD, 1, -1, 41),
                new Word(Opcode.JMPIL, 4, 7, -1),
                new Word(Opcode.JMPIE, 4, 7, -1),
                new Word(Opcode.ADDI, 7, -1, 41),
                new Word(Opcode.LDI, 1, -1, 0),
                new Word(Opcode.STD, 1, -1, 41),
                new Word(Opcode.SUBI, 3, -1, 1),
                new Word(Opcode.JMPIE, 4, 3, -1),
                new Word(Opcode.ADDI, 3, -1, 1),
                new Word(Opcode.LDI, 2, -1, 1),
                new Word(Opcode.STD, 2, -1, 42),
                new Word(Opcode.SUBI, 3, -1, 2),
                new Word(Opcode.JMPIE, 4, 3, -1),
                new Word(Opcode.LDI, 0, -1, 43),
                new Word(Opcode.LDI, 6, -1, 25),
                new Word(Opcode.LDI, 5, -1, 0),
                new Word(Opcode.ADD, 5, 7, -1),
                new Word(Opcode.LDI, 7, -1, 0),
                new Word(Opcode.ADD, 7, 5, -1),
                new Word(Opcode.LDI, 3, -1, 0),
                new Word(Opcode.ADD, 3, 1, -1),
                new Word(Opcode.LDI, 1, -1, 0),
                new Word(Opcode.ADD, 1, 2, -1),
                new Word(Opcode.ADD, 2, 3, -1),
                new Word(Opcode.STX, 0, 2, -1),
                new Word(Opcode.ADDI, 0, -1, 1),
                new Word(Opcode.SUB, 7, 0, -1),
                new Word(Opcode.JMPIG, 6, 7, -1),
                new Word(Opcode.STOP, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1)
            }),
            new Program("PB", new Word[] {
                new Word(Opcode.LDI, 0, -1, 7),
                new Word(Opcode.STD, 0, -1, 50),
                new Word(Opcode.LDD, 0, -1, 50),
                new Word(Opcode.LDI, 1, -1, -1),
                new Word(Opcode.LDI, 2, -1, 13),
                new Word(Opcode.JMPIL, 2, 0, -1),
                new Word(Opcode.LDI, 1, -1, 1),
                new Word(Opcode.LDI, 6, -1, 1),
                new Word(Opcode.LDI, 7, -1, 13),
                new Word(Opcode.JMPIE, 7, 0, 0),
                new Word(Opcode.MULT, 1, 0, -1),
                new Word(Opcode.SUB, 0, 6, -1),
                new Word(Opcode.JMP, -1, -1, 9),
                new Word(Opcode.STD, 1, -1, 15),
                new Word(Opcode.STOP, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1)
            }),
            new Program("PC", new Word[] {
                new Word(Opcode.LDI, 7, -1, 5),
                new Word(Opcode.LDI, 6, -1, 5),
                new Word(Opcode.LDI, 5, -1, 46),
                new Word(Opcode.LDI, 4, -1, 47),
                new Word(Opcode.LDI, 0, -1, 4),
                new Word(Opcode.STD, 0, -1, 46),
                new Word(Opcode.LDI, 0, -1, 3),
                new Word(Opcode.STD, 0, -1, 47),
                new Word(Opcode.LDI, 0, -1, 5),
                new Word(Opcode.STD, 0, -1, 48),
                new Word(Opcode.LDI, 0, -1, 1),
                new Word(Opcode.STD, 0, -1, 49),
                new Word(Opcode.LDI, 0, -1, 2),
                new Word(Opcode.STD, 0, -1, 50),
                new Word(Opcode.LDI, 3, -1, 25),
                new Word(Opcode.STD, 3, -1, 99),
                new Word(Opcode.LDI, 3, -1, 22),
                new Word(Opcode.STD, 3, -1, 98),
                new Word(Opcode.LDI, 3, -1, 38),
                new Word(Opcode.STD, 3, -1, 97),
                new Word(Opcode.LDI, 3, -1, 25),
                new Word(Opcode.STD, 3, -1, 96),
                new Word(Opcode.LDI, 6, -1, 0),
                new Word(Opcode.ADD, 6, 7, -1),
                new Word(Opcode.SUBI, 6, -1, 1),
                new Word(Opcode.JMPIEM, -1, 6, 97),
                new Word(Opcode.LDX, 0, 5, -1),
                new Word(Opcode.LDX, 1, 4, -1),
                new Word(Opcode.LDI, 2, -1, 0),
                new Word(Opcode.ADD, 2, 0, -1),
                new Word(Opcode.SUB, 2, 1, -1),
                new Word(Opcode.ADDI, 4, -1, 1),
                new Word(Opcode.SUBI, 6, -1, 1),
                new Word(Opcode.JMPILM, -1, 2, 99),
                new Word(Opcode.STX, 5, 1, -1),
                new Word(Opcode.SUBI, 4, -1, 1),
                new Word(Opcode.STX, 4, 0, -1),
                new Word(Opcode.ADDI, 4, -1, 1),
                new Word(Opcode.JMPIGM, -1, 6, 99),
                new Word(Opcode.ADDI, 5, -1, 1),
                new Word(Opcode.SUBI, 7, -1, 1),
                new Word(Opcode.LDI, 4, -1, 0),
                new Word(Opcode.ADD, 4, 5, -1),
                new Word(Opcode.ADDI, 4, -1, 1),
                new Word(Opcode.JMPIGM, -1, 7, 98),
                new Word(Opcode.STOP, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1)
            })
        };
    }
}
