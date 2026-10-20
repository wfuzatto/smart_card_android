# Smart Tap e compatibilidade com fechaduras BIS

## Resultado provisório da análise arquitetural (não é resultado de ensaio)

- MIFARE Classic 1K depende de comandos específicos e autenticação Crypto1 para setores. O Android HostApduService opera em ISO-DEP/APDUs ISO 7816-4 e não fornece substituição transparente de UID, autenticação Classic ou setores.
- Google Wallet Generic Pass é um objeto visual/QR; salvar passe não habilita por si só a transmissão NFC interoperável com leitores de fechadura.
- Smart Tap é uma integração distinta, não é um SELECT AID arbitrário ou uma biblioteca HCE local. **Não está configurado ou implementado no laboratório**.
- O leitor ACR122U pode se comunicar com cartões e Android HCE por PC/SC, mas **não é automaticamente um terminal Smart Tap certificado**.
- Sem documentação do controlador BIS/Be-Tech/Saga e testes autorizados, não há evidência de que as fechaduras aceitem ISO-DEP, Smart Tap ou credencial no smartphone.

## Smart Tap — dependências externas

Conforme a documentação oficial:
- Identificar o **Redemption Issuer** e o **Collector ID** do comerciante.
- Habilitar recursos apropriados na conta emissora do Google Wallet (acesso sujeito a políticas e eventual aprovação).
- Cadastrar **chave pública de autenticação com versão**; configurar terminal com Collector ID e os componentes criptográficos autorizados.
- Vincular à classe o parâmetro \`enableSmartTap\` e \`redemptionIssuers\`, e ao objeto o \`smartTapRedemptionValue\` ASCII, só quando a integração Smart Tap tiver sido habilitada corretamente.
- Implantar terminal **compatível/certificado** e integração de validação no ponto de coleta; não assumir que o ACR122U ou fechadura BIS suportam esse protocolo.
- Avaliar com Google/fornecedor disponibilidade para **novos emissores**, homologação, responsabilidades de chaves e custos antes de habilitar qualquer emissão NFC.

Fontes:
- https://developers.google.com/wallet/smart-tap/introduction/communication-flow
- https://developers.google.com/wallet/smart-tap/introduction/issuer-configuration
- https://developers.google.com/wallet/reference/rest/v1/genericobject
- https://developers.google.com/wallet/access/hotel-key/get-started/overview

A Google também documenta um produto separado **Google Wallet Hotel Keys**; avaliar elegibilidade, parceiros/fechaduras suportados, homologação e integração comercial, sem assumir equivalência com Generic Pass nem Smart Tap.

## Veredito atual

| Questão | Evidência atual |
|---|---|
| Cartão Google Wallet criado? | Não testado com Issuer real |
| Cartão adicionado a uma Wallet? | Não testado em aparelho |
| Android comunica-se via HCE com ACR122U? | Implementação construída; sem ensaio físico |
| MIFARE Classic emulado pelo HCE? | Não: limitação estrutural do Android HCE |
| BIS aceita smartphone? | Não demonstrado |
| Smart Tap disponível? | Configuração, aprovação e terminal não verificados |

Manter todos os testes **fora da operação de produção**, com cartão de teste, número de quarto fictício e acompanhamento do responsável pelas fechaduras.
