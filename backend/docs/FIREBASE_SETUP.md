# Firebase Storage — configuração e validação

Este guia ativa o provedor `firebase` do backend. **Enquanto ele não for feito, o sistema funciona
com `APP_STORAGE_PROVIDER=local`** (volume Docker + URLs temporárias assinadas pelo backend), que
serve para desenvolvimento, testes e demonstração, mas **não é** o Firebase.

> Estado da validação: o código de integração com o Cloud Storage foi escrito e coberto por testes
> com um `Storage` simulado. **Nenhum teste foi executado contra um bucket real** (não havia
> credencial, nome de bucket confirmado nem verificação do plano). Use a seção 6 para validar.

## 1. Papéis de cada parte

| Componente | Responsabilidade |
|---|---|
| PostgreSQL | Usuários, turmas, metadados do material e **somente o caminho do objeto** (`materiais/AAAA/MM/<uuid>.<ext>`). Nunca uma URL. |
| Firebase / Cloud Storage | Bytes dos PDFs e vídeos, em bucket **privado**. |
| Backend | Valida arquivo e permissão, envia ao bucket, gera Signed URL V4 curta após autorizar, audita. |
| Frontend | Nunca vê credencial nem caminho interno; só recebe `{ url, expiraEm }`. |

O Firebase Authentication **não** é usado (a autenticação é o JWT próprio). Firestore/Realtime
Database **não** são usados.

## 2. Pré-requisitos no Firebase Console (ação da equipe)

1. **Projeto**: `ideia-sapce` (ID exatamente como informado; confira no Console, em
   *Configurações do projeto*, se corresponde ao ID real).
2. **Plano Blaze**: desde 3 de fevereiro de 2026, projetos no plano Spark não têm acesso aos
   buckets do Cloud Storage for Firebase. Confirme em *Uso e faturamento* qual é o plano atual.
   Alterar o plano é decisão e responsabilidade da equipe; o código não contorna essa exigência.
   Defina um **alerta de orçamento** no Google Cloud Billing.
3. **Bucket**: em *Build → Storage*, crie/identifique o bucket e copie o **nome exato**
   (ex.: aparece como `gs://NOME`). Use apenas o nome, sem `gs://`.
4. **Regras do Storage** (aba *Rules*): negue todo acesso de clientes. O backend usa credencial de
   serviço, que não depende dessas regras.
   ```
   rules_version = '2';
   service firebase.storage {
     match /b/{bucket}/o {
       match /{allPaths=**} {
         allow read, write: if false;
       }
     }
   }
   ```
5. **Acesso público bloqueado** (recomendado) e acesso uniforme no nível do bucket:
   ```
   gcloud storage buckets update gs://NOME_DO_BUCKET \
     --public-access-prevention --uniform-bucket-level-access
   ```
6. **Proteção contra exclusão acidental** (recomendado; o sistema usa exclusão lógica e preserva o
   objeto, mas uma falha entre upload e commit pode deixar objetos órfãos):
   ```
   gcloud storage buckets update gs://NOME_DO_BUCKET --versioning
   ```
   (a retenção por *soft delete* do Cloud Storage também ajuda; verifique o padrão do bucket).

## 3. Conta de serviço (credencial do backend)

1. Google Cloud Console → *IAM e administrador → Contas de serviço* (projeto `ideia-sapce`):
   crie uma conta dedicada, por exemplo `ideiaspace-backend-storage`.
2. Conceda a ela, **apenas no bucket**, o papel `Storage Object Admin`
   (`roles/storage.objectAdmin`): permite criar, ler e remover objetos; não permite alterar o bucket.
3. Gere uma chave JSON e salve **fora do repositório** (ex.: `~/.config/ideiaspace/credencial.json`,
   permissão `chmod 600`). **Nunca** commite, cole em chat, documentação ou logs.
   - O `.gitignore` já ignora `secrets/`, `*-firebase-adminsdk-*.json`, `*service-account*.json`,
     `firebase-credentials*.json` e `credencial*.json`.
4. **Assinatura de URLs**: a Signed URL V4 precisa ser assinada pela chave da conta de serviço.
   Com a chave JSON, a assinatura é local e nenhuma permissão adicional é necessária. Sem chave
   (credencial do ambiente GCP), a conta precisaria do papel `Service Account Token Creator` sobre
   si mesma (`iam.serviceAccounts.signBlob`); sem isso a geração de URL falha com 503.

## 4. CORS do bucket (necessário para o leitor de PDF e para vídeo)

O PDF.js e o `<video>` buscam a Signed URL a partir da origem do frontend. Crie `cors.json`:

```json
[
  {
    "origin": ["http://localhost:5173", "http://localhost:3000"],
    "method": ["GET", "HEAD"],
    "responseHeader": ["Content-Type", "Content-Length", "Content-Range", "Accept-Ranges", "Range"],
    "maxAgeSeconds": 3600
  }
]
```

Aplicar (substitua as origens pelas de produção quando houver):

```
gcloud storage buckets update gs://NOME_DO_BUCKET --cors-file=cors.json
```

## 5. Variáveis de ambiente

| Variável | Valor | Observação |
|---|---|---|
| `APP_STORAGE_PROVIDER` | `firebase` | Padrão é `local`. |
| `FIREBASE_PROJECT_ID` | `ideia-sapce` | Como informado pela equipe. |
| `FIREBASE_STORAGE_BUCKET` | `[nome exato do bucket]` | **Obrigatória** com `firebase`; o backend recusa iniciar o provedor sem ela. Não há valor padrão. |
| `GOOGLE_APPLICATION_CREDENTIALS` | caminho local do JSON | Lida pelas Application Default Credentials. |
| `APP_STORAGE_SIGNED_URL_TTL_SECONDS` | `900` | 30–3600 s. Vídeos longos: o *seek* após expirar exige nova URL. |

Docker Compose (arquivo `.env` ao lado do `compose.yaml`, que não vai para o Git):

```
APP_STORAGE_PROVIDER=firebase
FIREBASE_PROJECT_ID=ideia-sapce
FIREBASE_STORAGE_BUCKET=<nome exato>
FIREBASE_CREDENTIALS_FILE=/caminho/seguro/credencial.json
```

O compose monta esse arquivo **somente-leitura** em `/run/secrets/firebase-credentials.json` e
aponta `GOOGLE_APPLICATION_CREDENTIALS` para ele dentro do container.

Execução local sem Docker:

```
export APP_STORAGE_PROVIDER=firebase
export FIREBASE_PROJECT_ID=ideia-sapce
export FIREBASE_STORAGE_BUCKET=<nome exato>
export GOOGLE_APPLICATION_CREDENTIALS=/caminho/seguro/credencial.json
./mvnw spring-boot:run
```

## 6. Como validar com o bucket real (checklist)

1. Suba o backend com as variáveis acima; o log deve indicar o provedor `firebase` e o bucket.
   A credencial não deve aparecer em nenhum log.
2. Como DOCENTE vinculado a uma turma, envie um PDF (`POST /api/v1/conteudos`, multipart).
   Esperado: `201`, e um objeto novo em `materiais/AAAA/MM/…` no Console do Storage.
3. No PostgreSQL (como `postgres`), confira que `conteudo.storage_path` guarda apenas o caminho
   (nunca `https://`) e que `mime_type`, `tamanho_bytes` e `sha256` conferem com o arquivo.
4. Publique o material (`PATCH /api/v1/conteudos/{id}/status`) e, como ALUNO da turma, abra
   `GET /api/v1/conteudos/{id}/acesso`. Esperado: `200` com `url` contendo
   `X-Goog-Algorithm=GOOG4-RSA-SHA256` e `X-Goog-Expires`; a URL baixa o PDF dentro da validade
   e responde `403` depois dela.
5. Como ALUNO de outra turma, ou com o material em RASCUNHO: `404`.
6. Conferir a auditoria (`GET /api/v1/auditoria`, ADMIN): eventos `ABRIU_PDF`/`INICIOU_VIDEO` com
   `ipOrigem` e `userAgent` do servidor e `TENTATIVA_ACESSO_NEGADO` para os bloqueios.
7. Substituir o arquivo (`PUT /api/v1/conteudos/{id}` com novo `arquivo`): o objeto antigo some do
   bucket somente depois do commit; o novo permanece.
8. Teste de acesso público: abra a URL do objeto sem assinatura
   (`https://storage.googleapis.com/NOME/materiais/...`); deve retornar `403`/`AccessDenied`.

Somente depois de executar esse roteiro com sucesso a integração pode ser considerada validada
com o Firebase real.

## 7. Observações de segurança

- A Signed URL contém o nome do objeto (inerente ao mecanismo). Ela é efêmera e nunca é gravada no
  banco, nos logs de auditoria nem persistida no frontend além da sessão do leitor.
- Qualquer pessoa que receba uma URL válida a acessa até expirar; por isso o prazo é curto e só é
  emitida após a autorização do backend e da RLS.
- Eventos de download/impressão registrados são *tentativas detectadas no navegador*, não uma
  garantia de bloqueio.
- Rode `mvn dependency:tree` e confirme a versão de `google-cloud-storage` (declarada no `pom.xml`)
  em um ambiente com acesso ao Maven Central; atualize para a versão estável vigente se desejar.
