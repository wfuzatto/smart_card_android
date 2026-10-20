# Hotel NFC Wallet Lab

Protótipo **experimental e separado de produção**, para comparar Google Wallet Generic Pass, Android ISO-DEP HCE, PC/SC ACR122U e cartões MIFARE Classic 1K utilizados pelo BIS Hotel (Be-Tech/Saga).

**NÃO** altera bis_api, btlock57L.dll, AcsReader.dll, o firmware das fechaduras ou chaves de cartões. Criar um objeto Google Wallet, salvar um passe e autenticar uma fechadura são três operações independentes.

## Estrutura

- app/: Android Kotlin, Jetpack Compose, Google Wallet PayClient SDK (savePassesJwt), QR ZXing, HostApduService
- backend/: FastAPI, SQLite, REST Google Wallet, JWT RS256 assinados no servidor, OpenAPI /docs
- windows/HotelNfcPcscLab/: ferramenta console C#/.NET 8 para leitores PC/SC, incluindo ACR122U
- docs/: onboarding Google, Smart Tap, plano de testes, limites de compatibilidade
- .github/workflows/ci.yml: testes e builds; disponibiliza APK debug como artifact **somente após execução CI com sucesso**

## Primeiros passos

### Backend (Python 3.11+)
1. A partir da raiz, execute: cd backend
2. python -m venv .venv
3. Ative a venv: Windows \`.venv\Scripts\activate\`; Linux \`source .venv/bin/activate\`
4. pip install -r requirements.txt
5. Copie \`.env.example\` para \`.env\`. Configure LAB_API_KEY com pelo menos 20 caracteres aleatórios.
6. Para criar credenciais locais, basta a LAB_API_KEY. Para emitir na Google Wallet real, siga [docs/GOOGLE_WALLET.md](docs/GOOGLE_WALLET.md).
7. uvicorn app.main:app --host 0.0.0.0 --port 8000

Verificação: http://127.0.0.1:8000/health e http://127.0.0.1:8000/docs.
É possível também executar scripts/start-backend.ps1 ou scripts/start-backend.sh após configurar .env.

Exemplo de emissão local:
\`\`\`bash
curl -X POST http://127.0.0.1:8000/v1/credentials \
 -H "X-Lab-Key: SUA_CHAVE_LAB" -H "Content-Type: application/json" \
 -d '{"hotel_name":"Hotel Laboratório","guest_name":"Pessoa Teste","room_number":"101","checkin":"2026-10-20T14:00:00-03:00","checkout":"2026-10-22T12:00:00-03:00"}'
\`\`\`
A resposta traz UUID e identificador opaco de QR/HCE. Depois faça POST /v1/credentials/{uuid}/wallet para criar classe/objeto na API oficial, obter JWT e link de salvamento. Esse endpoint retorna 503 se não houver credenciais de emissor configuradas e 502 quando a chamada real à Google falha; não simula emissão.

**Segurança do laboratório:** o token LAB_API_KEY protege as rotas administrativas, mas não é autenticação multiusuário. Não exponha o backend na internet; use VLAN segregada/VPN, HTTPS se remoto, limite de acesso e não use dados reais de hóspedes. Android permite HTTP claro apenas para desenvolvimento local em LAN isolada. Não inclua LAB_API_KEY nos APKs distribuídos nem use este serviço para produção. QR e HCE carregam tokens estáticos experimentais: não devem abrir portas.

### Android

- Android Studio recente, JDK 17, Android SDK API 35; abra a raiz como projeto Gradle.
- Use \`gradle :app:testDebugUnitTest :app:assembleDebug\` (Gradle 8.9).
- APK: \`app/build/outputs/apk/debug/app-debug.apk\`.
- A instalação também pode ser feita por \`adb install -r app/build/outputs/apk/debug/app-debug.apk\`.
- Alternativamente, abra GitHub > Actions > último build verde > artifact **hotel-nfc-wallet-lab-debug-apk**. Não confunda código enviado com compilação concluída.
- Na interface Android, informe o IP/porta do computador com FastAPI. \`10.0.2.2\` funciona somente em emulador Android. Informe a LAB_API_KEY manualmente, sem persistência no app.
- Crie a credencial, emita GenericObject **real** no servidor, depois clique Adicionar à Carteira do Google. A prova de salvamento neste celular é o retorno \`RESULT_OK\` do PayClient, observado no aplicativo. O endpoint de consulta Google confirma existência do objeto, **não** se este usuário individual o salvou.
- O botão deve utilizar a arte oficial Google Wallet, obtida conforme as [diretrizes de botão](https://developers.google.com/wallet/generic/android) antes de distribuição externa. Neste protótipo, é apresentado em texto no Material 3; a chamada ao SDK é real.

### Android HCE

Ative NFC, desbloqueie a tela, gere credencial e habilite modo HCE no aplicativo.
AID de laboratório: \`F0484F54454C01\`.
APDUs:
- SELECT AID: \`00 A4 04 00 07 F0 48 4F 54 45 4C 01\` -> \`90 00\`.
- GET DATA: \`80 CA 00 00 00\` -> bytes ASCII do token opaco + \`90 00\`.
- Sem habilitação/seleção -> \`69 85\`; AID incorreto -> \`6A 82\`; instrução desconhecida -> \`6D 00\`.

HCE é ISO-DEP/ISO 14443-4. **Não oferece MIFARE Classic / Crypto1 / setores / UID configurável.** É intencionalmente incompatível com autenticação proprietária de fechaduras existentes.

### Windows PC/SC (.NET 8)

\`\`\`powershell
dotnet restore windows/HotelNfcPcscLab/HotelNfcPcscLab.csproj
dotnet run --project windows/HotelNfcPcscLab/HotelNfcPcscLab.csproj
\`\`\`
Selecione leitor listado, teste A (cartão Classic), B (celular HCE) ou C (Wallet), aproxime o dispositivo e pressione Enter. A ferramenta coleta ATR, protocolo PC/SC, envia SELECT AID de teste e, no modo B com seleção bem sucedida, GET DATA. Gera JSON/TXT datados.

**PC/SC T=1 e pseudo-ATR não provam tecnologia RF ISO-DEP.** MIFARE Classic pode ser detectado como presença de cartão, mas isso não prova autenticação. O teste de Google Wallet não deve reivindicar Smart Tap apenas porque detectou um celular.

## Estado real e limitações
- Backend e app geram/consomem **credenciais experimentais**; a chamada real Google depende de Issuer ID, service account e habilitação autorizada.
- As validações com ACR122U e fechaduras físicas dependem de execução presencial; o repositório não contém tais medições.
- Smart Tap requer configurações específicas, IDs, chaves e terminal compatível. ACR122U não equivale automaticamente a terminal Smart Tap.
- Nenhum teste de integração com BIS é considerado aprovado sem relatório de campo.

Veja [Google Wallet](docs/GOOGLE_WALLET.md), [Plano de testes](docs/TEST_PLAN.md) e [Smart Tap / BIS](docs/COMPATIBILITY.md).
