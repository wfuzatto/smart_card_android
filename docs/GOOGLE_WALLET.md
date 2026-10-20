# Configuração oficial da Google Wallet API (Generic Pass)

Fonte primária: https://developers.google.com/wallet/generic e https://developers.google.com/wallet/generic/android

1. Acesse a [Google Pay & Wallet Business Console](https://pay.google.com/business/console). Crie uma conta emissora Google Wallet, habilite **Demo Mode** e anote o **Issuer ID** numérico. Se a interface exigir aprovação, siga os requisitos do console.
2. Em https://console.cloud.google.com crie/selecione um projeto Google Cloud com administração própria do laboratório.
3. Em APIs & Services ative **Google Wallet API**.
4. Em IAM > Service Accounts crie uma service account para o backend. Gere credencial JSON apenas se necessário para execução local, guarde **fora do GitHub e do APK**; use armazenamento de segredos em ambientes hospedados.
5. No Google Wallet Business Console compartilhe o acesso de emissor com o e-mail da service account, concedendo as permissões necessárias para gerir classes e objetos. Um papel IAM no Google Cloud sozinho não substitui o acesso ao Issuer.
6. No console, adicione os e-mails Google autorizados a testar passes em **Demo Mode**. Em modo demo, os cartões poderão exibir **[TEST ONLY]**.
7. No computador backend edite \`backend/.env\`:
   - WALLET_ISSUER_ID=seu_id
   - WALLET_CLASS_SUFFIX=hotel_nfc_lab
   - GOOGLE_APPLICATION_CREDENTIALS=/caminho/absoluto/service-account.json
   - LAB_API_KEY=chave-longa-de-laboratorio
8. Inicie FastAPI, crie credencial em POST /v1/credentials. Emita via POST /v1/credentials/{uuid}/wallet. O backend usa OAuth2 de service account para a REST API \`walletobjects.googleapis.com\`, cria classe se ausente, cria o objeto e assina JWT RS256 no servidor.
9. No Android, clique o botão após receber o JWT. O SDK executa \`PayClient.savePassesJwt\`. Salvar é confirmado no app **somente se RESULT_OK**. Valide manualmente o cartão no Google Wallet instalado.
10. Valide hotel, hóspede, quarto, validade e QR no passe real. Para expirar/revogar, faça PATCH /v1/credentials/{uuid}. A consulta GET /v1/credentials/{uuid}/wallet-status acessa a REST API e informa se o objeto existe.

**Diferenças de evidência**:
- API REST HTTP 200/201 ao criar GenericObject prova apenas que o objeto foi criado na API.
- Link e JWT assinados não provam que alguém salvou.
- RESULT_OK do SDK prova sucesso do fluxo de salvamento neste aparelho.
- Campo remoto \`hasUsers\` (quando presente) pode não identificar um usuário específico; não use como prova de salvamento individual.
- Cartão visível com QR não prova comportamento NFC, Smart Tap ou compatibilidade BIS.

**Boas práticas:** para um teste real use contas fictícias e dados não sensíveis. A credencial permanece um passe visual/QR, sem chaves de porta. Não exponha JWT/token em logs públicos. Para produção, substitua autenticação simples de laboratório por auth per-user, HTTPS, auditoria forte, políticas de privacidade e rotação de credenciais.

Links úteis:
- https://developers.google.com/wallet/generic/use-cases/create
- https://developers.google.com/wallet/generic/use-cases/jwt
- https://developers.google.com/wallet/reference/rest/v1/genericobject
- https://developers.google.com/wallet/generic/use-cases/expired-passes
- https://developers.google.com/wallet/generic/android
