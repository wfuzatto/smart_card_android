# Plano de teste com evidência verificável

Nenhum resultado de hardware ou Wallet é previamente marcado como aprovado. Preencher data, dispositivo, versão Android, versão Google Play Services, firmware de leitor, número de série, ambiente e operador. Salvar JSON/TXT do diagnóstico PC/SC e captura de tela do fluxo Wallet, com informações pessoais ocultas.

| ID | Teste | Esperado | Evidência exigida | Estado inicial |
|---|---|---|---|---|
| 01 | POST /v1/credentials | HTTP 201 + UUID + token opaco persistidos | HTTP response e GET | Não testado no hardware |
| 02 | POST /{uuid}/wallet com Issuer válido | GenericClass/GenericObject reais e JWT RS256 | API Google + object_id | Bloqueado até configurar Issuer |
| 03 | Salvar pelo Android PayClient | RESULT_OK e passe na Wallet | Callback SDK e verificação visual | Bloqueado até conta autorizada |
| 04 | Conteúdo visual Google | hotel, quarto, hóspede, validade e QR | Captura de tela da Wallet real | Não testado |
| 05 | Android HCE + PCSC | SELECT AID 9000, GET DATA opaco + 9000 | Logs Android/Windows correlacionados | Não testado |
| 06 | MIFARE Classic 1K | Detecção não confundida com autenticação | PC/SC ATR, erros e leitor BIS separado | Não testado |
| 07 | Wallet sem HCE | Reportar AID/Protocolo realmente observado | JSON PC/SC, captura RF se possível | Não testado |
| 08 | Smartphone próximo à fechadura BIS | Observar resposta sem alterar produção | Vídeo autorizado e registro do teste | Não testado |
| 09 | Revogar credencial | API Google PATCH para INACTIVE + SQLite | API REST, nova exibição Wallet | Não testado |
| 10 | Backend unitário | pytest sem chamadas externas reais | CI backend/job concluído | Aguardando CI |
| 11 | Android APDU unitário | testes SELECT, GET DATA, erros | CI Android/job concluído | Aguardando CI |

## Matriz de diferenciação

| Pilha | Protocolo objetivo | O que sucesso significa |
|---|---|---|
| BIS/MIFARE Classic | ISO 14443-3 + autenticação proprietária Crypto1 | Autenticou *com cartão físico* na fechadura, em teste autorizado |
| Android HCE | ISO-DEP / ISO 14443-4 + APDUs 7816-4 | AID experimental e GET DATA respondidos ao ACR122U |
| Wallet Generic Pass | JWT/REST + Wallet app; QR visual | Cartão salvo com RESULT_OK e conferência visual |
| Wallet Smart Tap | Protocolo certificado, coletor, chaves e terminal | Só com aprovação e terminal Smart Tap validado |

## Sete perguntas BIS

1. **Detecta o telefone?** Testar leitor físico e registrar atividade (não deduzir de ACR122U).
2. **Responde à seleção NFC inicial?** Medir RF/anticollision de forma não invasiva ou documentação do fabricante.
3. **Tenta autenticação Classic?** Confirmar com documentação/logs oficiais; não inferir de falha.
4. **Android completa Crypto1?** HCE padrão não implementa Classic/Crypto1; não tentar contorno.
5. **ISO-DEP possível?** Verificar controlador da fechadura e software/firmware do fabricante.
6. **Wallet altera resposta?** Repetir com HCE desativado e Wallet salva, registrar observáveis separados.
7. **Tecnologia alternativa?** Consultar Be-Tech/Saga documentação/catálogo e suporte técnico; qualquer mudança só em bancada autorizada.

Registro do ensaio: \`data, operador, hotel, reader_model, atr, pcsc_protocol, apdu_tx, apdu_rx, elapsed_ms, tecnologia_confirmada, evidencias, limites, conclusão\`.
