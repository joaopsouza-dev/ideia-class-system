# Orientações de desenvolvimento

- Use o [README](README.md) para iniciar o projeto e localizar documentação técnica específica.
- Preserve alterações locais e não leia, registre ou copie valores de arquivos `.env` reais.
- Não versione credenciais, tokens, chaves privadas ou arquivos de conta de serviço.
- Mantenha `app_backend` sem `SUPERUSER` e sem `BYPASSRLS`; não enfraqueça políticas RLS para contornar regras de acesso.
- Faça mudanças de banco em migrations Flyway aditivas. Não altere migrations já aplicadas.
- Execute testes de integração apenas contra o banco isolado `ideiaspace_test`.
- Não remova volumes Docker nem dados de banco em tarefas comuns.
- Para alterações funcionais, rode os testes relacionados; em alterações só documentais, valide links e `git diff --check`.
