# Roadmap — Keycloak / RHBK Operations

Atualizado em 9 de outubro de 2026. Apresentação: **15 de janeiro de 2027** (ano inferido da próxima ocorrência da data informada). Duração ainda não informada. Datas abaixo são metas de planejamento, não capacidades já entregues ou garantias de prazo. A meta H1 de 25/set passou e sua aceitação continua aberta; não foi reprogramada nesta entrega.

## Objetivo

Produzir análises verificáveis de infraestrutura, configuração, saúde, desempenho e uso de identidade em Keycloak/RHBK. Humanos e agentes usam REST/UI/MCP sobre os mesmos serviços; regras determinísticas estabelecem avaliações e políticas; a IA explica evidências e distingue fatos, hipóteses e lacunas. Público: administração IAM, plataforma/SRE, arquitetura, segurança e customer engagements, desde um diagnóstico pontual até uma frota.

Não substituir Admin Console, Terraform/GitOps, Prometheus, SIEM ou BI. A contribuição principal é conhecimento operacional rastreável e útil, não cobertura completa da Admin API.

Direção ampliada aprovada: manter **a aplicação do operador** para inspeção,
assessments, health checks, evidências e relatórios, e oferecer **cliente/IA via MCP**
como interface adicional para perguntas sobre infraestrutura, realms, usuários,
grupos, roles, políticas e clients. As duas interfaces usam os mesmos serviços e
regras de acesso; a IA não substitui a aplicação nem decide a autorização. "Tudo
sobre o ambiente" significa cobertura incremental por capacidade/fonte/versão,
com lacunas explícitas, não acesso irrestrito ou promessa de resposta universal.

## Compromisso da apresentação

Fluxo essencial: **descobrir RHBK → avaliar infraestrutura/HA e segurança → executar health checks → consultar métricas → produzir relatório → explicar evidências com IA**. Descoberta significa inventariar um target cadastrado/autorizado, não varrer redes arbitrárias. Não depende de administração ampla, remediação automática, execução de SPIs de terceiros ou previsão financeira.

Se RHBK/OpenShift não forem realmente validados, não apresentar Community como RHBK. Especificação, roteiro e go/no-go: [Demo readiness](milestones/2027-01-demo-readiness.md).

## Estado de partida e versões

**9 de outubro — fundação ONB1 de propriedade, revisões e auditoria:**
[ADR 0014](adr/0014-registry-ownership-before-managed-writes.md) e
[contrato](architecture/registry-ownership.md) adicionam V11 conservadora: cadastros
legados não são adotados automaticamente, reinicializações idênticas não gravam nada,
alterações reais têm revisão ORM e auditoria atômica, sem fallback de banco para
configuração. [Validação](development/onb1-ownership-2026-10-09.md) usa banco descartável.
Upgrade com colisões de IDs antigos exige adoção revisada, ainda pendente. Próximo:
adoção/transferência governada e cadastro público auditável, sem afrouxar os gates.

**9 de outubro — ONB1 parcial, pré-validação administrativa 0.1.0:**
[Contrato](registry-preflight.md) REST fechado por padrão, identidade/cliente explícitos,
entrada limitada e verificação local de IDs/referências. Não cadastra, concede acesso,
resolve segredos nem contata o destino. [Evidências](development/onb1-preflight-2026-10-09.md)
separam testes unitários/HTTP sintéticos da futura aceitação OIDC real. Próximo incremento:
propriedade/revisões e contrato transacional/auditável de cadastro e bootstrap;
sem mudar os gates H1/D1/AGT1, AGT2 parcial ou OP1 planejado.

**9 de outubro — arquitetura de instalação, sem implementação:**
[ADR 0012](adr/0012-operator-managed-portable-platform.md),
[contrato inicial 0.1](architecture/operator-installation-contract.md) e
[OP1](milestones/op1-operator-installation.md) separam Operator instalador, núcleo
portátil e coletores opcionais. Não há CRD/controlador/bundle entregue, novo acesso
ao cluster ou mudança de versão/runtime. Cadastro e bootstrap explícito de acesso
ficam no ONB1; H1/D1/AGT1 e AGT2 parcial mantêm seus gates. O ledger da
[entrega documental](development/operator-architecture-2026-10-09.md) distingue as
verificações locais de documentação das evidências históricas de execução.

Incremento atual: [AGT2 — assistência operacional por acesso](milestones/agt2-access-aware-assistance.md),
com [arquitetura](architecture/access-aware-operations-assistant.md) e ADR 0011.
Preserva a aplicação do operador. O primeiro [contrato de consultas restritas](configuration-reads.md)
implementa campos booleanos de realm/client por escopo, papel, cliente autenticado e
canal, com tela própria e serviço compartilhado UI/REST/MCP. **AGT2 parcial**:
sem grants habilitados por padrão, sem ampliar AGT1 e sem restringir automaticamente
permissões legadas por target. O [cliente de referência 0.1.0](../dev/access-aware-client/README.md)
implementa composição limitada em protótipo offline: catálogo, seleção explícita,
leituras e referências com limites e invalidação de contexto. O adaptador local já foi
exercitado com OIDC/MCP reais; implantação geral do host e demais domínios continuam
pendentes. Escritas permanecem em P2. Sem modelo habilitado.
[Evidência inicial](development/agt2-configuration-reads-2026-09-19.md) e
[validação local autenticada com dois operadores](development/agt2-authenticated-operators-2026-09-19.md).
O laboratório Community confirmou o recorte UI/REST/MCP; não substitui RHBK/OpenShift,
recriação real de recursos ou avaliação de modelo. [Evidência do cliente](development/agt2-client-2026-09-21.md):
99 testes próprios e 497 regressões Node, sem IA. A
[validação seguinte](development/agt2-client-live-2026-09-22.md) passou **72 checks
reais duas vezes**, com **534 regressões Node**, sem IA. Próximo: consolidar revisão
H1/escopo da release experimental e reprodução independente do operador antes da
publicação condicionalmente autorizada. Não implica suporte RHBK/OpenShift ou revogação.

Incremento anterior: [catálogo SecOps & IAM](development/secops-iam-scenarios.md),
com 39 casos sintéticos para auditoria de política de senha (IAM-06), investigação
de eventos (SECOPS-01), acesso temporário (HLP-01) e contenção separadamente aprovada
(SECOPS-02). Os dois primeiros pertencem a P1; alterações de usuários dependem dos
controles de P2. São backlog e dados de aceitação, não ferramentas implementadas
nem novas permissões do agente. [Validação e limites](development/secops-catalogue-2026-09-19.md).
Fila H1/D1/AGT1, versões do produto e datas preservadas; nenhuma chamada de modelo.

Incremento anterior: [alinhamento local do contrato AGT1](development/agt1-contract-alignment-2026-09-19.md).
O perfil **0.2.1** publica os limites já exigidos pelo validador e testa o alinhamento
entre instruções, descritor e código. O leitor offline distingue avisos do cliente de
ações observadas, sem atestar isolamento. Os três resultados anteriores do perfil
0.2.0 continuam rejeitados e preservados; nenhum novo modelo foi chamado. Baseline
backend **1705 aprovados / 9 ITs opcionais não executados**, Java 21 via jenv; limpeza
local confirmada. Próximo passo: autorização delimitada para reavaliar o perfil
corrigido, depois integração MCP autenticada e repetição. A aceitação completa de
H1/D1/AGT1 segue aberta; versões do produto e datas permanecem inalteradas.

Incremento anterior: [achados estruturados e rastreáveis AGT1](development/agt1-grounding-2026-09-19.md).
O MCP agora fornece achados/evidências sanitizados do próprio relatório, com vínculo
explícito ao ambiente/assessment, índices originais, limites e contagem de omissões.
O perfil **0.2.0** valida esses dados, preserva referências exatas e limita o pacote
serializado a **256 KiB**. Backend final **1705 testes aprovados / 9 ITs opcionais não
executados**, UI **195**, builds aprovados. O ledger registra os contratos e o ensaio
real local. Nenhum modelo/provedor foi ativado; próximo passo é escolher explicitamente
cliente/modelo e escopo autorizado dos dados, antes da integração e das avaliações
semânticas/adversariais repetidas. H1/D1/AGT1 completos e reprodução independente
continuam abertos; versões do produto, datas e requisito de D2 aprovado permanecem.

Incremento anterior: [base do agente de referência AGT1](development/agt1-profile-2026-09-19.md).
Perfil independente de provedor **0.1.0**, ferramenta única de relatório, ambiente
fixado pelo operador, projeção de fatos e validação estrutural de referências. O
modo sem IA funciona localmente; **nenhum modelo/provedor foi ativado**. São **45
testes novos** do perfil/CLI, **202** contratos no conjunto final, baseline backend
**1684**, UI **195**, build e **81 checks reais OIDC/MCP** aprovados, com limpeza
verificada. Validação estrutural não prova veracidade/segurança da narrativa; revisão
semântica continua obrigatória. Próximo passo: achados/evidências estruturados e
vinculados ao relatório, depois integração do cliente/provedor escolhido e avaliações
repetidas do modelo. H1/D1 e aceitação AGT1 seguem abertos; datas e versões do produto
não mudam. O perfil 0.1.0 não é uma release do produto.

Incremento anterior: [negativos reais de autenticação no navegador](development/d1-browser-negatives-2026-09-19.md).
Emissor incorreto, audiência incorreta e token expirado receberam 401 real e bloquearam
a interface; controle válido recebeu 200 e mostrou apenas o ambiente autorizado.
Assinatura, emissor, audiência e validade foram observados separadamente. A expiração
usa atraso controlado de transporte, não renovação normal nem resposta simulada.
Baseline backend **1684**, UI **195**, build, **142 testes de runner/contratos**, **15
do fixture** e **75 checks reais de identidade** passaram; limpeza independente sem
resíduos. Fecha apenas o critério local de navegador do D1, junto às evidências
anteriores de sessão. H1/D1, reprodução independente por operador e AGT1 continuam
abertos. Próximo passo local: **agente de referência AGT1**; uso de provedor/modelo
externo exige configuração e escopo de dados explícitos. Datas, versões e D2 aprovado
não mudam. As filas descritas nos incrementos anteriores são históricas.

Incremento anterior: [isolamento da navegação de mudanças](development/d1-change-navigation-2026-09-19.md).
Detalhes e listas pertencem à visita atual de mudança/ambiente/filtro; respostas e
ações antigas não substituem a tela nem liberam controles de outra operação. IDs
incompatíveis são rejeitados e cliques duplicados bloqueados. Baseline backend **1684**,
UI final **195 testes**, build aprovado; falhas anteriores preservadas no ledger.
Navegador validado com componentes reais e HTTP **sintético**, sem novas permissões ou
escritas administrativas reais. Botões secundários e seleção dos filtros foram ajustados
localmente; fixture encerrada e inventário limpo. H1/D1 seguem abertos. Próximos itens:
negativos restantes de navegador (issuer/audience/expiração), depois AGT1. Datas,
versões e exigência de laboratório D2 aprovado permanecem iguais.

Incremento anterior: [sessão e transporte no navegador](development/d1-browser-session-2026-09-19.md).
Requisições e eventos ficam vinculados à geração de autenticação; logout/troca de
sessão cancela o transporte antigo e descarta respostas atrasadas. Um 401 atual remove
as telas protegidas; 403 por ambiente não encerra a sessão válida. Baseline backend
**1684**, UI final **160 testes**, build aprovado. Evidências reais de navegador e
limpeza estão no ledger, separadas dos testes simulados. Não implementa revogação
instantânea de JWT/SSE nem rollback de operações já recebidas pelo servidor.
H1/D1 continuam abertos; próximos itens locais são a navegação obsoleta em ChangeDetail,
os negativos restantes de navegador e AGT1. Datas, versões e exigência de D2 aprovado
permanecem iguais.

Incremento anterior: [metadados de leitura/autorização de ambiente](development/h1-read-metadata-2026-09-19.md).
O endpoint de ambiente verifica READ no target antes da descoberta. Leituras e
históricos de saúde usam cópias filtradas após a avaliação; identidades canônicas e
contagens tipadas são preservadas em caminhos explícitos. Dez famílias MCP compartilham
erros sem causas originais. Coletores, regras, vínculos e estado operacional não mudam.
Baseline **1469**, validação final **1684 testes backend**, nove ITs opcionais não
executados; **215 novas invocações**. Ensaios locais e limpeza têm evidências próprias,
incluindo cinco novos checks de autorização de ambiente. Próximo trabalho local:
negativos de navegador/revogação de sessão, depois AGT1. Segredos desconhecidos,
identidades preservadas, diagnósticos internos e cobertura geral continuam limitações;
H1/D1 seguem abertos. Não muda datas, versões nem a exigência de D2 aprovado.

Incremento anterior: [metadados de mudanças/auditoria](development/h1-change-metadata-2026-09-19.md).
Entradas e observações inseguras são recusadas antes de se tornarem planos ou valores
aplicados; históricos usam cópias filtradas sem regravar hashes/estado. O read-back
inseguro permanece inconclusivo. Auditoria opcional e erros REST/MCP de mudanças
recebem projeção segura, preservando identidade, escopo e auditoria obrigatória.
Baseline **1385**, validação final **1469 testes backend**, nove ITs opcionais não
executados; ensaios locais e limpeza têm evidências próprias no ledger. Continuam
abertos os caminhos genéricos de metadados/tools, negativos de navegador/revogação,
AGT1 e aceitação H1/D1. Não muda datas, versões, permissões ou exigência de D2 aprovado.

Incremento anterior: [proteção de metadados](development/h1-metadata-trust-2026-09-19.md).
Relatórios, snapshots e achados usam uma projeção explícita após a avaliação,
ocultando credenciais reconhecíveis sem mudar regras, estados ou configurações
aplicadas. O Markdown parte do JSON sanitizado; metadados continuam dados sem
autoridade. Leituras históricas preservam registros e hashes originais; novos hashes
usam inventário sanitizado. Validação final: **1385 testes backend**, nove ITs opcionais
não executados; os ensaios locais e a limpeza têm evidências próprias no ledger.
Continuam abertos os demais caminhos legados de metadados/mutações/auditoria,
negativos de navegador/revogação, AGT1 e aceitação H1/D1. Não é proteção universal
contra segredos ou prompt injection. Versões, permissões e datas não mudam.

Incremento anterior: [capacidades observadas e cobertura](development/h1-capability-coverage-2026-09-19.md).
O tipo cadastrado não determina mais a coleta de Routes/config OpenShift; versões
observadas, desconhecidas e não suportadas são explícitas. Inventário, assessment,
health e relatório compartilham a decisão conservadora de completude. É uma primeira
reconciliação de cluster, não o modelo completo de autorização/freshness de todas as
fontes. Metadados, negativos de navegador/revogação e AGT1 seguem na fila local;
H1/D1 permanecem abertos. Datas, permissões e versões de desenvolvimento não mudam.

- O incremento local D1 alinha backend e metadados de aplicação/MCP/OpenAPI em `0.8.1-SNAPSHOT`, e UI/lockfile em `0.8.1-dev.0`. São versões de desenvolvimento, sem publicação ou conclusão automática de milestone; ver [versionamento](development/release-versioning.md).
- Marcos 0.1–0.8 são históricos, não certificação de produção.
- 0.8.1: Slices 1–3 administrativas implementadas; Slice 4 de realms **adiada para P2**. Marco não é automaticamente concluído.
- 0.8.2: relatórios sob demanda e confirmação de instalação em targets existentes entregues localmente; novo cadastro de conexões/targets e evidência retida/replay permanecem incompletos.
- Controles essenciais de 0.8.3 são antecipados para H1.
- D1 avançou com versões alinhadas, [observações de navegador](development/d1-browser-workflow-2026-09-11.md), [saúde/visão geral](development/d1-health-corrections-2026-09-11.md), [retorno seguro OIDC](development/d1-return-path-2026-09-11.md) e [métricas/relatórios](development/d1-metrics-report-2026-09-11.md). Os registros históricos preservam seus testes e limites: versão ausente não provoca falsa indisponibilidade; dados não coletados permanecem desconhecidos; REST/MCP e navegador mostram métricas observadas e lacunas por target.
- A [correção de confiança dos relatórios](development/d1-report-trust-2026-09-11.md) preserva snapshots e valida a política explícita de memória somente no laboratório. O limite ausente explicava a evidência de pressão de memória não emitida; demais limites não foram inventados.
- O [incremento de instalação de 18/09](development/d1-installation-identity-2026-09-18.md) valida descoberta e confirmação com identidade OIDC local real e API de cluster sintética, incluindo seleção explícita, negações e auditoria. A identidade de setup é separada; leitores e permissões padrão permanecem iguais. Os ensaios automatizados repetidos passaram; no navegador, foram verificados login por deep link, seleção e aceite explícitos, revisão do UID, confirmação persistida/auditada, logout e controles de setup desabilitados para o leitor comum. O outro target permaneceu inalterado. Restam endurecimento H1 de confiança/providers/engine/limites, revisão de dependências, negativos mais amplos de navegador e o protótipo AGT1. H1/D1 continuam abertos; nenhum cluster real foi acessado por este incremento.
- H1/D1…D5/P1…P4 são trilhas de entrega, não versões. Números antigos permanecem rastreáveis; próxima release será numerada após aceitação, sem reescrever histórico.

Incremento anterior: [limites Admin e diagnósticos](development/h1-admin-boundaries-2026-09-18.md). Coletas validam corpos, tokens, listas e campos conhecidos antes de aceitar evidências; respostas inválidas e realms divergentes permanecem parciais, sem contagens favoráveis inventadas. O cliente de coleta recusa categorias de diagnóstico inseguras sem alterar o logging do operador; administração comum e escritas ficam inalteradas. UI/dependências/versões/permissões inalteradas. A reconciliação inicial de cluster foi implementada no incremento acima; cobertura geral de fontes, metadados e gates de confiança restantes continuam abertos. Fonte, testes, limites e limpeza ficam no ledger; [prazo compartilhado](development/h1-compound-collection-2026-09-18.md), [inventário](development/h1-inventory-envelopes-2026-09-18.md), [prontidão da coleta](development/h1-scrape-readiness-2026-09-18.md) e [métricas/credenciais](development/h1-operation-budgets-2026-09-18.md) preservam seus resultados históricos.

Incremento anterior: [H1 falhas e limites de resposta](development/h1-failure-bounds-2026-09-18.md), com saúde inconclusiva preservada, limites HTTP/parser/tempo por resposta e remoção de estatísticas temporais artificiais. Seus **496 testes backend / 133 UI** e ensaios locais permanecem históricos; a [revisão original de dependências](development/h1-dependency-review-2026-09-18.md) tem a remediação subsequente registrada abaixo, sem certificação ampla de segurança ou de RHBK/OpenShift.

## Calendário até janeiro

Continuação de 19 de setembro: [validação dos runners locais](development/d1-runner-validation-2026-09-19.md). O encerramento passa a resolver erro transitório de consulta somente após confirmar que o grupo de processos desapareceu; incerteza persistente continua bloqueando uma nova execução. O ledger reúne a suíte de regressão, o SQL restrito ao PostgreSQL descartável e os ensaios locais. Esse avanço operacional mantém H1/D1 abertos para os gates restantes e não altera as datas nem o ambiente aprovado exigido por D2.

Recuperação local: o [desligamento forçado explicitamente autorizado](development/h1-admin-boundaries-recovered-2026-09-18.md) permitiu restaurar o Podman e remover somente recursos verificados dos ensaios. Na repetição, 70 checks e verificação de JWT passaram; o runner terminou com erro de limpeza por SSH EOF (exit 1). A limpeza separada passou (exit 0), com inventário final sem containers/volumes, portas livres e imagens preservadas. Rever limites/robustez do runner antes de uso desassistido; causas da instabilidade continuam desconhecidas. Resultados backend/métricas/instalação anteriores e falhas históricas são preservados. Isso não altera metas nem libera H1/D1/D2.

Correção de inventário e métricas temporais: [ledger e limites](development/h1-evidence-temporal-2026-09-18.md).
Warnings seguros, evidências desconhecidas preservadas, coleta parcial propagada e
validação da grade de avaliações retornada pelo Prometheus; medições instantâneas
não substituem janelas incompletas. **561 testes backend**, 9 ITs opt-in não
executados; interface, dependências e versões do produto inalteradas. Permanecem
limites globais de operação/inventário, sanitização geral de metadados, cobertura
dos scrapes/instâncias, negativos/revogação no navegador e AGT1. H1/D1 seguem abertos;
metas, permissões e exigência de ambiente D2 aprovado não mudam.

Correção subsequente de dependências: [ledger de migração](development/h1-dependency-fix-2026-09-18.md), com Quarkus 3.39.4/MCP 1.13.2, clientes Keycloak alinhados e Router 7/Vite 7/Vitest 4; CI/build UI em Node 24 LTS. **496 backend / 133 UI** e builds passaram; auditorias npm completa/runtime agora sem alertas reportados. Os ensaios locais de identidade/métricas, navegador e instalação foram repetidos. A prioridade volta às lacunas de inventário/evidência, limites globais/cobertura temporal, negativos/revogação e AGT1; varredura ampla do backend/imagens e revisão de suporte antes do freeze continuam necessárias. Não altera versões do produto, metas, permissões ou o gate de cluster real.

As [especificações executáveis](milestones/README.md#executable-milestones--current-plan) desdobram este roadmap em escopo, dependências, responsáveis funcionais e critérios testáveis. Após o preflight e a fundação V11 ONB1, próximo trabalho de produto: **adoção/transferência governada e cadastro auditável ONB1**, preservando os gates locais H1/D1, reprodução independente e avaliação AGT1 com cliente/modelo/escopo aprovados antes das respectivas alegações de aceitação. D2 continua dependente de ambiente RHBK/OpenShift dedicado e explicitamente aprovado, podendo usar manifests revisados antes do OP1. O novo Operator não é obrigatório para a apresentação. Datas existentes foram preservadas, sem criar promessas para OP1, trilhas portáteis ou SCORE1.

| Marco | Meta | Entrega / dependências | Gate de saída | Responsável funcional |
|---|---|---|---|---|
| **H1 — Confiança básica** | 25/set/2026 | Correções implementadas localmente; fechamento da aceitação, manifesto da fonte e riscos residuais em [H1](milestones/h1-trust-closure.md). | Regressões; isolamento negativo; leitura sem escrita; política equivalente; limitações explícitas. | Desenvolvimento + revisão de segurança |
| **D1 — Fluxo local** | 16/out/2026 | Pacote executável, configuração externa, dois targets/identidades, diagnóstico e relatório. Depende H1. | Operador segue guia sem editar código; fixtures detectadas; permissões mínimas e cleanup testados. | Desenvolvimento + operador |
| **D2 — RHBK/OpenShift** | 13/nov/2026 | Ambiente dedicado, versões exatas, RBAC por namespace, credenciais read-only, Prometheus. Depende D1 e infraestrutura do mantenedor. | Discovery/HA/segurança/health/métricas executados em RHBK real; evidências sanitizadas. Sem execução: NOT VERIFIED. | Mantenedor/ambiente + desenvolvimento |
| **D3 — Documentos e uso IAM** | 4/dez/2026 | Coleta/proveniência, regras curadas, relatórios técnico/executivo, exportação/histórico; login/falhas/aplicações se houver fonte. Depende H1/D1 e D2 para alegações RHBK. | Achados rastreáveis; replay da evidência preservada; indicadores com denominadores testados; sem PII nos documentos para IA. | Desenvolvimento + revisores técnico/negócio |
| **D4 — Pilotos e freeze** | 18/dez/2026 | 3–5 pilotos propostos, ensaios, docs em inglês, snapshot e gravação alternativa. Depende D2/D3. | Sem defeito crítico no fluxo; duas execuções reproduzíveis; revisão externa de limitações. | Mantenedor + convidados |
| **D5 — Prontidão** | 8/jan/2027 | Reensaio congelado, certificados/acessos/quotas/rede, roteiro cronometrado, alegações dos slides. | Go/no-go revisado pelo apresentador; fallback conferido; nada novo sem ensaio. | Apresentador |
| **Apresentação** | **15/jan/2027** | Fluxo aprovado; identificar ao vivo/gravado e produto/versão. | Mostrar evidência e limites, além da resposta do modelo. | Apresentador |

19/dez–7/jan: estabilização/contingência, não grandes funcionalidades. Se D2 atrasar, cortar indicadores e formatos opcionais, nunca segurança ou honestidade sobre RHBK.

## Backlog completo

| ID | Capacidade | Entrega mínima e fonte | Limitação / gate | Prioridade |
|---|---|---|---|---|
| OPR-01 | Instalação por Operator | Controlador separado para lifecycle do Operations; CR de instalação, ownership, referências e status | OP1 planejado; não gerencia Keycloaks avaliados, concede acesso ou comprova HA/compatibilidade; contrato 0.1 não é CRD disponível | Após baseline; sem nova data ou obrigação para demo |
| INF-01 | Inventário | Workload, réplicas, pods, nós/zones, probes, requests/limits, PDB/HPA, services/rotas/ingress | APIs K8s/OpenShift; sem secrets; acesso negado não significa recurso inexistente | D2 |
| INF-02 | Disponibilidade | Avaliação por topologia, domínio de falha e réplicas | Configuração não prova failover; testes destrutivos somente em lab autorizado | D2 |
| INF-03 | Saúde | Admin API/OIDC/cluster/métricas/dependências observáveis, duração e timeout | Não inferir saúde interna do DB por mera conectividade | D2 |
| CFG-01 | Configurações válidas | Formato válido, suporte da versão e adequação ao contexto; realms/clients/URLs/PKCE/fluxos | Regra versionada/aplicável, não configuração universalmente correta | H1/D2 |
| CFG-02 | Drift | Histórico/diferenças com baseline e exceções intencionais | Respeitar origem Terraform/GitOps | D3 base / P1 completo |
| SCORE-01 | Nota operacional 1–100 | Rubrica determinística por dimensão/critério, perfil, produto/versão, pesos e limites por risco crítico | Pesos propostos exigem calibração; desconhecido não é aprovação; não é certificação oficial | SCORE1 planejado; candidato D3, sem nova data |
| SCORE-02 | Motivo da nota | Mesma nota e memória de cálculo em UI/API/MCP/relatórios; evidências, impacto e como melhorar | Cobertura/confiança separadas; não recalcular a partir de achados truncados | SCORE1 após rubrica; D3E/D3R para retenção/documentos |
| SCORE-03 | Evolução comparável | Replay e histórico com perfil, regras, versões, escopo e fontes preservados | Alterações de metodologia/versão/fonte não equivalem automaticamente a melhora ou regressão | SCORE1 com D3E; D2 para alegações RHBK |
| SCORE-04 | Ciclo de vida, patches e CVEs | Fontes públicas oficiais Red Hat/Keycloak, CSAF/VEX, matching de artefato/build e cache/offline com validade | Backports; sem match é desconhecido; versão antiga não prova CVE; sem envio de inventário ou aplicação automática de patches | SCORE1 planejado; validação de fontes e política antes da nota |
| PERF-01 | Runtime | Latência, erros, vazão, JVM/HTTP/DB/cache quando disponíveis | Selectors, unidades, janela, freshness e cobertura | D2 |
| IAM-01 | Login sucesso/falha | Eventos agregados, taxas e proporções com denominador | Fonte habilitada; zero eventos não é 100% sucesso | D3 incremento |
| IAM-02 | Aplicações mais usadas | Ranking de login por client/realm/período | Dimensão client pode faltar; limitar cardinalidade; client não é necessariamente produto de negócio | D3 incremento |
| IAM-03 | Usuários ativos | DAU/WAU/MAU definidos e deduplicados | Contadores não fornecem usuários únicos; fonte autorizada com pseudonimização/retenção | P1 |
| IAM-04 | MFA | Separar política exigida, cadastro e uso efetivo | Cadastro de fator não prova uso; instrumentação por jornada quando necessária | P1 |
| IAM-05 | Impacto de indisponibilidade | SLO, janelas, aplicações e tentativas observadas/estimadas | Downtime pode eliminar telemetria; impacto financeiro requer dados externos aprovados | P1 |
| IAM-06 | Auditoria de idade de senha | Política organizacional versionada, grupo efetivo, fonte autoritativa e motivo por resultado | Desconhecido/N/A e cobertura separados; 90 dias não é regra universal | P1 planejado; catálogo sintético |
| SECOPS-01 | Investigação de identidade | Eventos delimitados, deduplicação, origem e sinal geográfico determinístico | VPN/proxy e lacunas; indício não comprova comprometimento nem autoriza bloqueio | P1 planejado; catálogo sintético |
| HLP-01 | Acesso temporário | Plano tipado, sponsor, menor privilégio efetivo e expiração durável | Senha temporária não expira conta; aceitar recuperação/aprovação antes de implementar writes | P2 após gates; catálogo sintético |
| SECOPS-02 | Contenção de usuário | Plano separado, aprovação real, recuperação e conferência | Desabilitar, terminar sessões e revogar tokens são resultados distintos | P2 após gates; catálogo sintético |
| ALT-01 | Alertas | Limiares, duração, recuperação, deduplicação, silêncio com prazo e evidência | Sem dados é problema de observabilidade, não serviço saudável | P1; exemplo opcional D3 |
| ALT-02 | Fora do comum | Baseline por target/horário, histórico mínimo e sazonalidade | Anomalia não é incidente; medir falsos positivos; LLM não é detector | P1 após ALT-01 |
| DOC-01 | Relatório técnico | Escopo, versões, cobertura, assessment, health, métricas, evidências/recomendações | Gerador determinístico e resultado estruturado de referência | D3 |
| DOC-02 | Relatório executivo | Riscos, serviços observados afetados, tendências e ações | Fatos separados de hipóteses; nenhuma certificação automática | D3 |
| DOC-03 | Pacote de evidências | JSON/Markdown, schema/hashes, histórico/replay; PDF/DOCX da mesma fonte | Exportação precisa sanitização/autorização; formatos renderizados exigem QA visual | JSON/MD D3; PDF/DOCX opcional ou P1 |
| SPI-01 | Avaliar extensões | Artefato/checksum/build, SPI, SBOM/dependências, config sanitizada, compatibilidade | Sem fonte/artefato: NOT VERIFIED; metadata não prova segurança | P3; inventário opcional antes |
| SPI-02 | Executar testes customizados | Harness funcional/falha/carga/compatibilidade em runtime dedicado | Isolado do processo da plataforma e dos targets de clientes | P3 |
| SPI-03 | Promoção de SPI | Artefato revisado, pipeline, aprovação, canário/rollback | Nunca upload/deploy arbitrário via MCP | P3 posterior |
| GOV-01 | Governança | Identidade, target/ação, least privilege, auditoria/retenção; ACL realm/recurso quando necessária | Demonstrar negações em todos os transportes | H1 base / P2 completo |
| CHG-01 | Remediação | Política única, aprovação íntegra, execução durável/reconciliação | Demo read-only; locks próprios não eliminam escritores externos | H1 contenção / P2 completo |
| AGT-01 | Agente de referência opcional | Perfil versionado, roteiro read-only, exemplos, configuração MCP/OIDC sem secrets e guia de adaptação | Reutiliza regras da plataforma; validar ao menos uma combinação cliente/modelo/autenticação, sem prometer compatibilidade universal | D1 protótipo / D2 integração |
| AGT-02 | Fidelidade e segurança do agente | Casos de teste com evidências conhecidas, citações, dados ausentes, isolamento e metadata maliciosa | Nenhuma conclusão de saúde/score inventada; autorização no backend; dados enviados somente a provedores aprovados | D3 aceitação / D4 ensaio e freeze |
| AGT-03 | Perguntas operacionais por acesso | Composição limitada de consultas de infra/IAM com escopo, versão e evidência | Canal e cliente 0.1.0 exercitados com dois operadores OIDC/MCP locais; faltam host geral, demais domínios e RHBK | AGT2 parcial; sem nova data |
| AGT-04 | Aplicação e IA complementares | Console mantém avaliação/health/evidências/relatórios sem IA; UI/REST/MCP usam mesmos serviços | Mesmas observações/permissões produzem mesmos fatos; novas telas incrementais, chat embutido opcional | AGT2 + trilhas de domínio |

Especificações: [documentos confiáveis](architecture/trustworthy-reporting.md), [IAM/negócio](architecture/iam-business-observability.md), [SPIs](architecture/spi-assurance.md), [nota operacional explicável](architecture/operational-rating.md) e [milestone SCORE1](milestones/score1-explainable-rating.md). SCORE1 é backlog, não alteração do score atual nem da fila H1/D1/AGT1; seu uso na apresentação depende de aceitação, sem ampliar os compromissos de D3.

<a id="agente-de-referência--planejado-ainda-não-implementado"></a>

## Agente de referência — perfil inicial em protótipo; expansão planejada

AGT1 **0.2.1** é o perfil local inicial de relatório; integração/aceitação completa
continuam abertas. [AGT2](milestones/agt2-access-aware-assistance.md) tem cliente
independente 0.1.0 para composição restrita sem IA, exercitado com transporte local real;
demais perguntas multi-ferramenta, acesso fino e paridade evoluem incrementalmente.
Isso não altera o contrato AGT1, não habilita modelo e não transforma
grants por target em permissões pessoais do RHBK. A aplicação permanece primeira
interface de operação e revisão independente; nenhum fluxo de avaliação exige IA.

Perfil opcional e adaptável para um assistente conectado ao MCP, não um novo motor de avaliação nem um LLM obrigatório dentro do OpenShift. UI/API e relatórios determinísticos continuam funcionando sem IA. A organização pode usar seu próprio agente; nenhum fornecedor ou modelo é escolhido por este roadmap.

**Pacote previsto:** instruções versionadas de operação/explicação, exemplos de perguntas e respostas fundamentadas, configuração MCP e autenticação por referências seguras, guia de adaptação e conjunto de avaliações. Trata-se de material de produto reutilizável; não de prompts temporários dos agentes de desenvolvimento.

**Fluxo de referência:** listar targets autorizados → selecionar o ambiente lógico já cadastrado → executar assessment, health e métricas disponíveis → gerar relatório → explicar achados citando IDs e evidências. Selecionar um target não é confirmar/revincular sua instalação (BIND), que fica fora do agente de referência. Não inventar IDs ou recursos indisponíveis; tratar o conteúdo coletado como dados, nunca como novas instruções.

**Limites:** apenas READ/ASSESS nos targets autorizados, sem PLAN/APPROVE/WRITE, shell, endpoints arbitrários ou execução de SPIs. Grants e políticas são impostos pelo backend, não pelo prompt. Separar observação, hipótese e recomendação; não recalcular severidade, score ou PASS/FAIL. Credenciais do target nunca chegam ao modelo. Sanitização não autoriza automaticamente envio externo: a organização aprova o provedor e o escopo dos dados.

**Entrega e dependências:**

- D1 (16/out): protótipo local e guia de conexão, dependentes do contrato MCP e das fronteiras de identidade H1/D1.
- D2 (13/nov): validar autenticação e operação read-only contra o RHBK/OpenShift escolhido, registrando cliente MCP, modelo e versões efetivamente usados.
- D3 (4/dez): aceitar o perfil e suas avaliações junto dos relatórios; depende das evidências e contratos DOC-01/DOC-03 disponíveis. Não alegar replay antes de implementado.
- D4 (18/dez): ensaiar e congelar a combinação validada para a apresentação; mudanças de modelo/perfil exigem reavaliação. Manter a demonstração determinística sem IA como alternativa, explicitamente identificada.

**Critérios de aceitação:** conectar seguindo o guia sem editar o backend; produzir explicações rastreáveis; preservar resultados PARTIAL/UNKNOWN e janelas de coleta; distinguir falha de acesso de recurso inexistente; não vazar canários ou dados de outro target; não obedecer a instruções inseridas nas evidências; receber negação do backend para escritas/acessos indevidos. Executar casos normais, fontes ausentes, credenciais inválidas, tentativas de cruzar targets e metadata maliciosa. Registrar modelo, cliente, revisão do perfil, casos e resultados; zero violações críticas na suíte é um gate, não garantia universal de segurança. A plataforma deve produzir os mesmos achados determinísticos com ou sem o agente.

Responsáveis funcionais: desenvolvimento do perfil e integração, revisão técnica/de segurança e apresentador no ensaio. Requisitos relacionados: FR-ASSESS-003/005, FR-MCP-001, SEC-AI-001/002, SEC-MULTI-001 e SEC-REPORT-001. O agente permanece opcional para adoção do produto, embora seja a implementação de referência prevista para a parte conversacional da apresentação.

## Arquitetura de destino

### Instalação OpenShift-native, operação multiplataforma

Direção aceita em [ADR 0012](adr/0012-operator-managed-portable-platform.md): Operator
separado instala e reconcilia somente o Operations; o núcleo modular mantém console,
REST/MCP, autorização e avaliações determinísticas. Conexões diretas explícitas são
o padrão para fontes alcançáveis no mesmo OpenShift, outros clusters e VMs/containers.
Coletores opcionais atendem limites de rede/host; isolamento total requer importação
offline. Não há collector ou Operator implementado por esta decisão.

[Contrato 0.1](architecture/operator-installation-contract.md): uma réplica, OIDC,
read-only, referências locais, banco/IdP externos, ownership sem disputa CR/UI,
status de instalação distinto de saúde dos targets e preservação de dados na exclusão.
O exemplo é deliberadamente não aplicável. ONB1 cobre cadastro, bootstrap de acesso
e a política administrativa sem desligar silenciosamente o gate BIND/read-only.
D2 pode anteceder OP1; PORT1/PORT2 e P4 continuam donos dos coletores e prontidão.
Modelo 1:N de instalações, HA, migrações e distribuição exigem aceitação própria.

### Discovery portátil — direção aprovada, entrega incremental

O ambiente lógico (`targetId`) não depende de OpenShift. Separar conexões autorizadas, ambientes, instalações confirmadas e inventários observados; hosting, runtime e gerenciamento são dimensões distintas. Cobertura exigida: OpenShift/Kubernetes, VM/servidor físico, Docker, Podman, Compose, JVM standalone e extensão para outros runtimes. API administrativa, health e métricas disponíveis continuam úteis sem coletor de infraestrutura.

Sequência: fundação sem fallback de cluster → onboarding/vínculos explícitos → coletor local Podman/Compose e validação Docker separada → coletor VM/standalone e evidência offline sanitizada → histórico/diferenças e regras por capacidade. Não substitui o gate D2 RHBK/OpenShift nem cria datas adicionais artificiais. Novo cadastro de conexões/targets ([ONB1](milestones/onb1-environment-registry.md)), coletores de containers ([PORT1](milestones/port1-container-inventory.md)) e host/offline ([PORT2](milestones/port2-host-offline-inventory.md)) permanecem planejados. Requisitos: FR-DISC-001–004, FR-INV-003, SEC-INFRA-004/005. Contratos em [discovery portátil](architecture/portable-environment-discovery.md).

Entregue localmente: [vínculo exato](development/exact-installation-binding-2026-09-11.md) no backend/configuração/V9; recursos recriados exigem reconfirmação e pods seguem ownership. Não representa onboarding completo nem validação em cluster real.

Também entregue: [associação de rede](development/installation-networking-2026-09-11.md) por seleção exclusiva de Services e backends de Ingress/Route. Testes simulados; ambiguidades e falhas permanecem lacunas. Conectividade, certificados e comportamento real dos controllers exigem o laboratório dedicado.

Entrega subsequente: [confirmação de instalação](development/installation-onboarding-2026-09-11.md) via REST e aba Installation, para targets existentes com conexão aprovada. Inclui candidatos expirantes, permissões DISCOVER/BIND, revalidação de UID/revisão e auditoria transacional. A [validação local com IdP real e API sintética](development/d1-installation-identity-2026-09-18.md) agora exercita esse caminho com identidade de setup separada e negações verificadas. Novo cadastro de targets/conexões, reconciliação completa, aceitação ampla de navegador e compatibilidade com cluster real permanecem pendentes. Nenhuma permissão padrão foi habilitada.

O D3 foi dividido em [D3E — evidência/replay](milestones/d3e-evidence-replay.md) e [D3R — documentos](milestones/d3r-trustworthy-documents.md), preservando a meta existente. [AGT1](milestones/agt1-reference-agent.md) reúne protótipo, integração e avaliação do agente sem criar dependência obrigatória de IA. [P1](milestones/p1-iam-continuous-observability.md), [P2](milestones/p2-governance-remediation.md), [P3](milestones/p3-spi-assurance.md) e [P4](milestones/p4-operational-readiness.md) especificam as evoluções posteriores.

Manter monólito modular e REST/MCP/UI como adaptadores. Fronteiras: identidade/targets, coleta/evidência, avaliação/documentos e mudanças. Uma coleta possui identidade, cobertura e proveniência; consumidores não refazem consultas sem declarar nova janela. PostgreSQL guarda histórico/coordenação; métricas ficam na fonte temporal. Replay usa evidências retidas, sem fingir observação ao vivo.

Modo pontual futuro: diagnóstico sobre target existente sem exigir PostgreSQL externo ou cluster. Modo serviço: persistência, UI, frota e agendamento. Execução SPI exige ambiente isolado próprio; não implica microserviços para o restante.

## Após a apresentação

| Trilha | Janela indicativa | Dependências e aceitação |
|---|---|---|
| **P1 — Observabilidade contínua/negócio** | 1º trimestre/2027 | D3, pilotos e fontes aprovadas. DAU/WAU/MAU, MFA, SLO/impacto, alertas/drift/retention. Precisão e ruído medidos. |
| **P2 — Governança/remediação** | Planejamento 1º/2º trimestre | Autorizações completas, tentativas duráveis, concorrência/crash e aprovação real. Retomar realm Slice 4; depois usuários/grupos/roles/flows/IdPs por demanda. |
| **P3 — SPI assurance** | Planejamento 2º trimestre | Threat model, artefatos autorizados, harness/runtime isolado, budgets e cleanup. Casos inseguros/timeout/recuperação comprovados. |
| **P4 — 1.0 operacional** | Sem data artificial | Matriz de suporte, migração/backup/restore, retenção/carga, revisão de segurança independente, recuperação e manutenção comunitária comprovadas. |

## Regras de execução e evidências

Segurança, integridade e alegações verdadeiras são inegociáveis. Mantenedor fornece ambiente/credenciais por canal seguro; não inserir secrets em chat/Git/relatório. Não habilitar dados sensíveis ou dimensões de alta cardinalidade automaticamente. “Completo” sempre significa completo para escopo/fontes declarados.

Testes locais: names/labels por projeto/execução, armazenamento temporário, cleanup obrigatório; volumes apenas com reutilização definida; nenhum prune global. Mudanças externas, publicação, merge, tags e implantação não são efeitos colaterais autorizados pelo roadmap.

Cada marco registra revisão, versões, testes/resultados/skips, data/fixtures e artefatos sanitizados. Documentos têm revisão de percentuais/denominadores/fontes/timestamps e concordância JSON/narrativa. IA tem avaliações de citações, desconhecidos, isolamento e instruções maliciosas em metadata.

Sucesso do produto: primeiro achado útil, achados confirmados, ruído/omissões, cobertura, tempo poupado, reuso e contribuições externas. Metas serão calibradas nos pilotos, não inventadas como resultados. Estado real: [project-state](project-state.md); histórico: [milestones](milestones/README.md).
