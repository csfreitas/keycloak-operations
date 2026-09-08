# Roadmap — Keycloak / RHBK Operations

Atualizado em 4 de setembro de 2026. Apresentação: **15 de janeiro de 2027** (ano inferido da próxima ocorrência da data informada). Duração ainda não informada. Datas abaixo são metas de planejamento, não capacidades já entregues ou garantias de prazo.

## Objetivo

Produzir análises verificáveis de infraestrutura, configuração, saúde, desempenho e uso de identidade em Keycloak/RHBK. Humanos e agentes usam REST/UI/MCP sobre os mesmos serviços; regras determinísticas estabelecem avaliações e políticas; a IA explica evidências e distingue fatos, hipóteses e lacunas. Público: administração IAM, plataforma/SRE, arquitetura, segurança e customer engagements, desde um diagnóstico pontual até uma frota.

Não substituir Admin Console, Terraform/GitOps, Prometheus, SIEM ou BI. A contribuição principal é conhecimento operacional rastreável e útil, não cobertura completa da Admin API.

## Compromisso da apresentação

Fluxo essencial: **descobrir RHBK → avaliar infraestrutura/HA e segurança → executar health checks → consultar métricas → produzir relatório → explicar evidências com IA**. Descoberta significa inventariar um target cadastrado/autorizado, não varrer redes arbitrárias. Não depende de administração ampla, remediação automática, execução de SPIs de terceiros ou previsão financeira.

Se RHBK/OpenShift não forem realmente validados, não apresentar Community como RHBK. Especificação, roteiro e go/no-go: [Demo readiness](milestones/2027-01-demo-readiness.md).

## Estado de partida e versões

- Artefato permanece `0.8.0-SNAPSHOT`; este plano não publica uma versão.
- Marcos 0.1–0.8 são históricos, não certificação de produção.
- 0.8.1: Slices 1–3 administrativas implementadas; Slice 4 de realms **adiada para P2**. Marco não é automaticamente concluído.
- 0.8.2: relatório sob demanda existe; onboarding e proveniência ainda incompletos.
- Controles essenciais de 0.8.3 são antecipados para H1.
- H1/D1…D5/P1…P4 são trilhas de entrega, não versões. Números antigos permanecem rastreáveis; próxima release será numerada após aceitação, sem reescrever histórico.

## Calendário até janeiro

| Marco | Meta | Entrega / dependências | Gate de saída | Responsável funcional |
|---|---|---|---|---|
| **H1 — Confiança básica** | 25/set/2026 | Escopo/completude das evidências, falso PASS/score, autorização REST/MCP, PKCE, integridade de planos e contratos UI. Iniciado nesta alteração. | Regressões; isolamento negativo; leitura sem escrita; política equivalente; limitações explícitas. | Desenvolvimento + revisão de segurança |
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
| INF-01 | Inventário | Workload, réplicas, pods, nós/zones, probes, requests/limits, PDB/HPA, services/rotas/ingress | APIs K8s/OpenShift; sem secrets; acesso negado não significa recurso inexistente | D2 |
| INF-02 | Disponibilidade | Avaliação por topologia, domínio de falha e réplicas | Configuração não prova failover; testes destrutivos somente em lab autorizado | D2 |
| INF-03 | Saúde | Admin API/OIDC/cluster/métricas/dependências observáveis, duração e timeout | Não inferir saúde interna do DB por mera conectividade | D2 |
| CFG-01 | Configurações válidas | Formato válido, suporte da versão e adequação ao contexto; realms/clients/URLs/PKCE/fluxos | Regra versionada/aplicável, não configuração universalmente correta | H1/D2 |
| CFG-02 | Drift | Histórico/diferenças com baseline e exceções intencionais | Respeitar origem Terraform/GitOps | D3 base / P1 completo |
| PERF-01 | Runtime | Latência, erros, vazão, JVM/HTTP/DB/cache quando disponíveis | Selectors, unidades, janela, freshness e cobertura | D2 |
| IAM-01 | Login sucesso/falha | Eventos agregados, taxas e proporções com denominador | Fonte habilitada; zero eventos não é 100% sucesso | D3 incremento |
| IAM-02 | Aplicações mais usadas | Ranking de login por client/realm/período | Dimensão client pode faltar; limitar cardinalidade; client não é necessariamente produto de negócio | D3 incremento |
| IAM-03 | Usuários ativos | DAU/WAU/MAU definidos e deduplicados | Contadores não fornecem usuários únicos; fonte autorizada com pseudonimização/retenção | P1 |
| IAM-04 | MFA | Separar política exigida, cadastro e uso efetivo | Cadastro de fator não prova uso; instrumentação por jornada quando necessária | P1 |
| IAM-05 | Impacto de indisponibilidade | SLO, janelas, aplicações e tentativas observadas/estimadas | Downtime pode eliminar telemetria; impacto financeiro requer dados externos aprovados | P1 |
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

Especificações: [documentos confiáveis](architecture/trustworthy-reporting.md), [IAM/negócio](architecture/iam-business-observability.md), [SPIs](architecture/spi-assurance.md).

## Arquitetura de destino

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
