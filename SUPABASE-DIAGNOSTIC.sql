-- Diagnostic en lecture seule : aucune modification de table, permission ou politique.
SELECT column_name, data_type FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'reported_numbers';
SELECT relrowsecurity, relforcerowsecurity FROM pg_class
WHERE oid = 'public.reported_numbers'::regclass;
SELECT policyname, roles, cmd, qual, with_check FROM pg_policies
WHERE schemaname = 'public' AND tablename = 'reported_numbers';
SELECT grantee, privilege_type FROM information_schema.role_table_grants
WHERE table_schema = 'public' AND table_name = 'reported_numbers';
SELECT count(*) AS numeros_eligibles FROM public.reported_numbers
WHERE reports >= 10 AND expires_at > now();
