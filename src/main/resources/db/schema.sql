create extension if not exists pgcrypto;

do $$
begin
    if not exists (select 1 from pg_type where typname = 'party_status') then
        create type party_status as enum ('ACTIVE', 'BLOCKED', 'DISABLED');
    end if;

    if not exists (select 1 from pg_type where typname = 'provider_status') then
        create type provider_status as enum ('ACTIVE', 'DEGRADED', 'DISABLED');
    end if;

    if not exists (select 1 from pg_type where typname = 'service_category') then
        create type service_category as enum ('TRANSFER', 'MOBILE_TOPUP', 'UTILITY', 'CARD_PAYMENT', 'WALLET');
    end if;

    if not exists (select 1 from pg_type where typname = 'currency_code') then
        create type currency_code as enum ('KGS', 'USD', 'EUR', 'KZT', 'UZS', 'RUB');
    end if;

    if not exists (select 1 from pg_type where typname = 'payment_status') then
        create type payment_status as enum ('CREATED', 'CHECK_REQUISITE', 'CONFIRMED', 'PROCESSING', 'SUCCESS', 'FAILED', 'CANCELLED');
    end if;

    if not exists (select 1 from pg_type where typname = 'attempt_status') then
        create type attempt_status as enum ('STARTED', 'RETRY', 'SUCCESS', 'FAILED');
    end if;

    if not exists (select 1 from pg_type where typname = 'provider_response_status') then
        create type provider_response_status as enum ('ACCEPTED', 'DECLINED', 'TIMEOUT', 'REQUISITE_INVALID', 'ERROR');
    end if;

    if not exists (select 1 from pg_type where typname = 'audit_action') then
        create type audit_action as enum (
            'CREATE_PAYMENT',
            'CHANGE_STATUS',
            'REGISTER_ATTEMPT',
            'REGISTER_PROVIDER_RESPONSE',
            'SYNC_REQUISITE'
        );
    end if;

    if not exists (select 1 from pg_type where typname = 'audit_result') then
        create type audit_result as enum ('SUCCESS', 'FAILURE', 'REPLAY');
    end if;

    if not exists (select 1 from pg_type where typname = 'requisite_status') then
        create type requisite_status as enum ('ACTIVE', 'INVALID', 'ARCHIVED');
    end if;
end $$;

create table if not exists clients (
    client_id varchar(80) primary key,
    client_name varchar(160) not null,
    client_type varchar(40) not null default 'INDIVIDUAL',
    status party_status not null default 'ACTIVE',
    created_at timestamptz not null default now()
);

create table if not exists providers (
    provider_id varchar(80) primary key,
    provider_name varchar(160) not null,
    api_url varchar(400) not null,
    status provider_status not null default 'ACTIVE',
    created_at timestamptz not null default now()
);

insert into providers (provider_id, provider_name, api_url, status)
values ('demo-provider', 'Demo Payment Provider', 'http://demo-provider.local/payments', 'ACTIVE')
on conflict (provider_id) do nothing;

create table if not exists payments (
    payment_id uuid primary key default gen_random_uuid(),
    client_id varchar(80) not null,
    provider_id varchar(80) not null default 'demo-provider',
    external_request_id varchar(120) not null,
    service_category service_category not null default 'TRANSFER',
    amount numeric(18, 2) not null check (amount > 0),
    currency currency_code not null,
    requisite varchar(160) not null,
    status payment_status not null default 'CREATED',
    failure_reason varchar(255),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table payments add column if not exists service_category service_category not null default 'TRANSFER';
alter table payments add column if not exists failure_reason varchar(255);

do $$
begin
    if exists (
        select 1 from information_schema.columns
        where table_name = 'payments' and column_name = 'service_category' and udt_name <> 'service_category'
    ) then
        alter table payments alter column service_category drop default;
        alter table payments
            alter column service_category type service_category
            using service_category::service_category;
        alter table payments alter column service_category set default 'TRANSFER'::service_category;
    end if;

    if exists (
        select 1 from information_schema.columns
        where table_name = 'payments' and column_name = 'currency' and udt_name <> 'currency_code'
    ) then
        alter table payments
            alter column currency type currency_code
            using currency::currency_code;
    end if;

    if exists (
        select 1 from information_schema.columns
        where table_name = 'payments' and column_name = 'status' and udt_name <> 'payment_status'
    ) then
        alter table payments alter column status drop default;
        alter table payments
            alter column status type payment_status
            using status::payment_status;
        alter table payments alter column status set default 'CREATED'::payment_status;
    end if;
end $$;

insert into clients (client_id, client_name)
select distinct p.client_id, concat('Client ', p.client_id)
from payments p
where not exists (select 1 from clients c where c.client_id = p.client_id);

insert into providers (provider_id, provider_name, api_url)
select distinct p.provider_id, concat('Provider ', p.provider_id), 'http://provider.local/payments'
from payments p
where not exists (select 1 from providers pr where pr.provider_id = p.provider_id);

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'fk_payments_client') then
        alter table payments
            add constraint fk_payments_client
            foreign key (client_id) references clients(client_id);
    end if;

    if not exists (select 1 from pg_constraint where conname = 'fk_payments_provider') then
        alter table payments
            add constraint fk_payments_provider
            foreign key (provider_id) references providers(provider_id);
    end if;
end $$;

create table if not exists idempotency_keys (
    key_id uuid primary key default gen_random_uuid(),
    client_id varchar(80) not null references clients(client_id),
    idempotency_key varchar(160) not null,
    request_hash varchar(128) not null,
    payment_id uuid not null references payments(payment_id) on delete cascade,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    unique (client_id, idempotency_key)
);

create table if not exists payment_attempts (
    attempt_id uuid primary key default gen_random_uuid(),
    payment_id uuid not null references payments(payment_id) on delete cascade,
    provider_id varchar(80) not null references providers(provider_id),
    attempt_number integer not null check (attempt_number > 0),
    status attempt_status not null,
    error_code varchar(80),
    started_at timestamptz not null default now(),
    finished_at timestamptz,
    unique (payment_id, attempt_number)
);

do $$
begin
    if exists (
        select 1 from information_schema.columns
        where table_name = 'payment_attempts' and column_name = 'status' and udt_name <> 'attempt_status'
    ) then
        alter table payment_attempts
            alter column status type attempt_status
            using status::attempt_status;
    end if;
end $$;

create table if not exists provider_responses (
    response_id uuid primary key default gen_random_uuid(),
    payment_id uuid not null references payments(payment_id) on delete cascade,
    provider_id varchar(80) not null references providers(provider_id),
    response_code smallint not null check (response_code between 100 and 599),
    response_status provider_response_status not null,
    received_at timestamptz not null default now()
);

do $$
begin
    if exists (
        select 1 from information_schema.columns
        where table_name = 'provider_responses' and column_name = 'response_status' and udt_name <> 'provider_response_status'
    ) then
        alter table provider_responses
            alter column response_status type provider_response_status
            using response_status::provider_response_status;
    end if;
end $$;

create table if not exists payment_status_history (
    history_id uuid primary key default gen_random_uuid(),
    payment_id uuid not null references payments(payment_id) on delete cascade,
    old_status payment_status,
    new_status payment_status not null,
    changed_at timestamptz not null default now()
);

do $$
begin
    if exists (
        select 1 from information_schema.columns
        where table_name = 'payment_status_history' and column_name = 'old_status' and udt_name <> 'payment_status'
    ) then
        alter table payment_status_history
            alter column old_status type payment_status
            using old_status::payment_status;
    end if;

    if exists (
        select 1 from information_schema.columns
        where table_name = 'payment_status_history' and column_name = 'new_status' and udt_name <> 'payment_status'
    ) then
        alter table payment_status_history
            alter column new_status type payment_status
            using new_status::payment_status;
    end if;
end $$;

create table if not exists audit_log (
    audit_id uuid primary key default gen_random_uuid(),
    payment_id uuid references payments(payment_id) on delete set null,
    action audit_action not null,
    result audit_result not null,
    details jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

alter table audit_log add column if not exists details jsonb not null default '{}'::jsonb;

do $$
begin
    if exists (
        select 1 from information_schema.columns
        where table_name = 'audit_log' and column_name = 'action' and udt_name <> 'audit_action'
    ) then
        alter table audit_log
            alter column action type audit_action
            using action::audit_action;
    end if;

    if exists (
        select 1 from information_schema.columns
        where table_name = 'audit_log' and column_name = 'result' and udt_name <> 'audit_result'
    ) then
        alter table audit_log
            alter column result type audit_result
            using result::audit_result;
    end if;
end $$;

create table if not exists payment_requisites (
    requisite_id uuid primary key default gen_random_uuid(),
    payment_id uuid not null unique references payments(payment_id) on delete cascade,
    account_number varchar(160) not null,
    receiver_name varchar(160) not null,
    status requisite_status not null default 'ACTIVE',
    created_at timestamptz not null default now()
);

alter table payments alter column payment_id set default gen_random_uuid();
alter table idempotency_keys alter column key_id set default gen_random_uuid();
alter table payment_attempts alter column attempt_id set default gen_random_uuid();
alter table provider_responses alter column response_id set default gen_random_uuid();
alter table payment_status_history alter column history_id set default gen_random_uuid();
alter table audit_log alter column audit_id set default gen_random_uuid();
alter table payment_requisites alter column requisite_id set default gen_random_uuid();

insert into payment_requisites (payment_id, account_number, receiver_name, status)
select p.payment_id, p.requisite, concat('Receiver ', right(p.requisite, least(length(p.requisite), 4))), 'ACTIVE'
from payments p
where not exists (
    select 1 from payment_requisites r where r.payment_id = p.payment_id
);

create or replace function ensure_payment_parties()
returns trigger
language plpgsql
as $$
begin
    insert into clients (client_id, client_name, client_type, status)
    values (new.client_id, concat('Client ', new.client_id), 'INDIVIDUAL', 'ACTIVE')
    on conflict (client_id) do nothing;

    insert into providers (provider_id, provider_name, api_url, status)
    values (new.provider_id, concat('Provider ', new.provider_id), 'http://provider.local/payments', 'ACTIVE')
    on conflict (provider_id) do nothing;

    return new;
end;
$$;

create or replace function set_payment_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create or replace function write_payment_insert_audit()
returns trigger
language plpgsql
as $$
begin
    insert into payment_status_history (payment_id, old_status, new_status)
    values (new.payment_id, null, new.status);

    insert into audit_log (payment_id, action, result, details)
    values (
        new.payment_id,
        'CREATE_PAYMENT',
        'SUCCESS',
        jsonb_build_object(
            'clientId', new.client_id,
            'providerId', new.provider_id,
            'amount', new.amount,
            'currency', new.currency
        )
    );

    return new;
end;
$$;

create or replace function write_payment_status_change()
returns trigger
language plpgsql
as $$
begin
    if old.status is distinct from new.status then
        insert into payment_status_history (payment_id, old_status, new_status)
        values (new.payment_id, old.status, new.status);

        insert into audit_log (payment_id, action, result, details)
        values (
            new.payment_id,
            'CHANGE_STATUS',
            'SUCCESS',
            jsonb_build_object(
                'oldStatus', old.status,
                'newStatus', new.status,
                'failureReason', new.failure_reason
            )
        );
    end if;

    return new;
end;
$$;

create or replace function sync_payment_requisite()
returns trigger
language plpgsql
as $$
begin
    insert into payment_requisites (payment_id, account_number, receiver_name, status)
    values (
        new.payment_id,
        new.requisite,
        concat('Receiver ', right(new.requisite, least(length(new.requisite), 4))),
        case when new.requisite ilike 'BAD%' then 'INVALID'::requisite_status else 'ACTIVE'::requisite_status end
    )
    on conflict (payment_id) do update
        set account_number = excluded.account_number,
            receiver_name = excluded.receiver_name,
            status = excluded.status;

    insert into audit_log (payment_id, action, result, details)
    values (
        new.payment_id,
        'SYNC_REQUISITE',
        'SUCCESS',
        jsonb_build_object('accountNumber', new.requisite)
    );

    return new;
end;
$$;

create or replace function audit_payment_attempt()
returns trigger
language plpgsql
as $$
begin
    insert into audit_log (payment_id, action, result, details)
    values (
        new.payment_id,
        'REGISTER_ATTEMPT',
        case when new.status in ('FAILED') then 'FAILURE'::audit_result else 'SUCCESS'::audit_result end,
        jsonb_build_object(
            'providerId', new.provider_id,
            'attemptNumber', new.attempt_number,
            'status', new.status,
            'errorCode', new.error_code
        )
    );

    return new;
end;
$$;

create or replace function audit_provider_response()
returns trigger
language plpgsql
as $$
begin
    insert into audit_log (payment_id, action, result, details)
    values (
        new.payment_id,
        'REGISTER_PROVIDER_RESPONSE',
        case
            when new.response_status in ('ACCEPTED') then 'SUCCESS'::audit_result
            else 'FAILURE'::audit_result
        end,
        jsonb_build_object(
            'providerId', new.provider_id,
            'responseCode', new.response_code,
            'responseStatus', new.response_status
        )
    );

    return new;
end;
$$;

create or replace function create_payment(
    p_payment_id uuid,
    p_client_id varchar,
    p_provider_id varchar,
    p_external_request_id varchar,
    p_service_category service_category,
    p_amount numeric,
    p_currency currency_code,
    p_requisite varchar
)
returns uuid
language plpgsql
as $$
begin
    insert into payments (
        payment_id,
        client_id,
        provider_id,
        external_request_id,
        service_category,
        amount,
        currency,
        requisite,
        status
    )
    values (
        p_payment_id,
        p_client_id,
        p_provider_id,
        p_external_request_id,
        p_service_category,
        p_amount,
        p_currency,
        p_requisite,
        'CREATED'
    );

    return p_payment_id;
end;
$$;

create or replace function create_payment_request(
    p_client_id varchar,
    p_provider_id varchar,
    p_amount numeric,
    p_currency currency_code,
    p_requisite varchar,
    p_idempotency_key varchar,
    p_request_hash varchar
)
returns table (
    payment_id uuid,
    client_id varchar,
    provider_id varchar,
    amount numeric,
    currency currency_code,
    status payment_status,
    idempotent_replay boolean
)
language plpgsql
as $$
declare
    v_existing record;
    v_payment_id uuid := gen_random_uuid();
    v_external_request_id varchar := concat('ext-', left(replace(v_payment_id::text, '-', ''), 12));
begin
    select
        p.payment_id,
        p.client_id,
        p.provider_id,
        p.amount,
        p.currency,
        p.status,
        i.request_hash
    into v_existing
    from idempotency_keys i
    join payments p on p.payment_id = i.payment_id
    where i.client_id = p_client_id
      and i.idempotency_key = p_idempotency_key;

    if found then
        if v_existing.request_hash <> p_request_hash then
            raise exception 'Idempotency-Key already used with different payload'
                using errcode = 'P0001';
        end if;

        return query
        select
            v_existing.payment_id,
            v_existing.client_id,
            v_existing.provider_id,
            v_existing.amount,
            v_existing.currency,
            v_existing.status,
            true;
        return;
    end if;

    perform create_payment(
        v_payment_id,
        p_client_id,
        p_provider_id,
        v_external_request_id,
        'TRANSFER',
        p_amount,
        p_currency,
        p_requisite
    );

    insert into idempotency_keys (
        client_id,
        idempotency_key,
        request_hash,
        payment_id,
        expires_at
    )
    values (
        p_client_id,
        p_idempotency_key,
        p_request_hash,
        v_payment_id,
        now() + interval '24 hours'
    );

    return query
    select
        p.payment_id,
        p.client_id,
        p.provider_id,
        p.amount,
        p.currency,
        p.status,
        false
    from payments p
    where p.payment_id = v_payment_id;
end;
$$;

create or replace function change_payment_status(
    p_payment_id uuid,
    p_new_status payment_status,
    p_failure_reason varchar default null
)
returns void
language plpgsql
as $$
begin
    update payments
    set status = p_new_status,
        failure_reason = coalesce(p_failure_reason, failure_reason)
    where payment_id = p_payment_id;

    if not found then
        raise exception 'payment % not found', p_payment_id using errcode = 'P0002';
    end if;
end;
$$;

create or replace function get_payment(p_payment_id uuid)
returns table (
    payment_id uuid,
    client_id varchar,
    provider_id varchar,
    amount numeric,
    currency currency_code,
    requisite varchar,
    status payment_status,
    failure_reason varchar,
    created_at timestamptz,
    updated_at timestamptz
)
language sql
stable
as $$
    select
        p.payment_id,
        p.client_id,
        p.provider_id,
        p.amount,
        p.currency,
        p.requisite,
        p.status,
        p.failure_reason,
        p.created_at,
        p.updated_at
    from payments p
    where p.payment_id = p_payment_id;
$$;

create or replace function list_payments(
    p_client_id varchar default null,
    p_limit integer default 50
)
returns table (
    payment_id uuid,
    client_id varchar,
    provider_id varchar,
    amount numeric,
    currency currency_code,
    requisite varchar,
    status payment_status,
    failure_reason varchar,
    created_at timestamptz,
    updated_at timestamptz
)
language sql
stable
as $$
    select
        p.payment_id,
        p.client_id,
        p.provider_id,
        p.amount,
        p.currency,
        p.requisite,
        p.status,
        p.failure_reason,
        p.created_at,
        p.updated_at
    from payments p
    where p_client_id is null or p.client_id = p_client_id
    order by p.created_at desc
    limit greatest(1, least(coalesce(p_limit, 50), 100));
$$;

create or replace function get_payment_history(p_payment_id uuid)
returns table (
    old_status payment_status,
    new_status payment_status,
    changed_at timestamptz
)
language sql
stable
as $$
    select h.old_status, h.new_status, h.changed_at
    from payment_status_history h
    where h.payment_id = p_payment_id
    order by h.changed_at asc;
$$;

create or replace function health_check()
returns text
language sql
stable
as $$
    select 'UP';
$$;

create or replace function health_check_json()
returns jsonb
language sql
stable
as $$
    select jsonb_build_object('status', health_check());
$$;

create or replace function create_payment_request_json(
    p_client_id varchar,
    p_provider_id varchar,
    p_amount numeric,
    p_currency currency_code,
    p_requisite varchar,
    p_idempotency_key varchar,
    p_request_hash varchar
)
returns jsonb
language sql
as $$
    select jsonb_build_object(
        'paymentId', r.payment_id,
        'clientId', r.client_id,
        'providerId', r.provider_id,
        'amount', r.amount::text,
        'currency', r.currency,
        'status', r.status,
        'idempotentReplay', r.idempotent_replay
    )
    from create_payment_request(
        p_client_id,
        p_provider_id,
        p_amount,
        p_currency,
        p_requisite,
        p_idempotency_key,
        p_request_hash
    ) r;
$$;

create or replace function get_payment_json(p_payment_id uuid)
returns jsonb
language sql
stable
as $$
    select jsonb_build_object(
        'paymentId', p.payment_id,
        'clientId', p.client_id,
        'providerId', p.provider_id,
        'amount', p.amount::text,
        'currency', p.currency,
        'requisite', p.requisite,
        'status', p.status,
        'failureReason', p.failure_reason,
        'createdAt', p.created_at,
        'updatedAt', p.updated_at,
        'history', coalesce(
            (
                select jsonb_agg(
                    jsonb_build_object(
                        'oldStatus', h.old_status,
                        'newStatus', h.new_status,
                        'changedAt', h.changed_at
                    )
                    order by h.changed_at asc
                )
                from get_payment_history(p_payment_id) h
            ),
            '[]'::jsonb
        )
    )
    from get_payment(p_payment_id) p;
$$;

create or replace function list_payments_json(
    p_client_id varchar default null,
    p_limit integer default 50
)
returns jsonb
language sql
stable
as $$
    select jsonb_build_object(
        'items',
        coalesce(
            jsonb_agg(
                jsonb_build_object(
                    'paymentId', p.payment_id,
                    'clientId', p.client_id,
                    'providerId', p.provider_id,
                    'amount', p.amount::text,
                    'currency', p.currency,
                    'requisite', p.requisite,
                    'status', p.status,
                    'failureReason', p.failure_reason,
                    'createdAt', p.created_at,
                    'updatedAt', p.updated_at
                )
                order by p.created_at desc
            ),
            '[]'::jsonb
        )
    )
    from list_payments(p_client_id, p_limit) p;
$$;

create or replace function register_payment_attempt(
    p_payment_id uuid,
    p_provider_id varchar,
    p_attempt_number integer,
    p_status attempt_status,
    p_error_code varchar default null
)
returns uuid
language plpgsql
as $$
declare
    v_attempt_id uuid := gen_random_uuid();
begin
    insert into payment_attempts (
        attempt_id,
        payment_id,
        provider_id,
        attempt_number,
        status,
        error_code,
        finished_at
    )
    values (
        v_attempt_id,
        p_payment_id,
        p_provider_id,
        p_attempt_number,
        p_status,
        p_error_code,
        case when p_status in ('SUCCESS', 'FAILED') then now() else null end
    );

    return v_attempt_id;
end;
$$;

create or replace function register_provider_response(
    p_payment_id uuid,
    p_provider_id varchar,
    p_response_code smallint,
    p_response_status provider_response_status
)
returns uuid
language plpgsql
as $$
declare
    v_response_id uuid := gen_random_uuid();
begin
    insert into provider_responses (
        response_id,
        payment_id,
        provider_id,
        response_code,
        response_status
    )
    values (
        v_response_id,
        p_payment_id,
        p_provider_id,
        p_response_code,
        p_response_status
    );

    return v_response_id;
end;
$$;

drop trigger if exists trg_payments_ensure_parties on payments;
create trigger trg_payments_ensure_parties
before insert on payments
for each row execute function ensure_payment_parties();

drop trigger if exists trg_payments_set_updated_at on payments;
create trigger trg_payments_set_updated_at
before update on payments
for each row execute function set_payment_updated_at();

drop trigger if exists trg_payments_insert_audit on payments;
create trigger trg_payments_insert_audit
after insert on payments
for each row execute function write_payment_insert_audit();

drop trigger if exists trg_payments_status_change on payments;
create trigger trg_payments_status_change
after update of status on payments
for each row execute function write_payment_status_change();

drop trigger if exists trg_payments_sync_requisite on payments;
create trigger trg_payments_sync_requisite
after insert or update of requisite on payments
for each row execute function sync_payment_requisite();

drop trigger if exists trg_payment_attempts_audit on payment_attempts;
create trigger trg_payment_attempts_audit
after insert on payment_attempts
for each row execute function audit_payment_attempt();

drop trigger if exists trg_provider_responses_audit on provider_responses;
create trigger trg_provider_responses_audit
after insert on provider_responses
for each row execute function audit_provider_response();

create index if not exists idx_clients_status
    on clients (status);

create index if not exists idx_providers_status
    on providers (status);

create index if not exists idx_payments_client_created
    on payments (client_id, created_at desc);

create index if not exists idx_payments_provider_created
    on payments (provider_id, created_at desc);

create index if not exists idx_payments_status
    on payments (status);

create index if not exists idx_payments_service_category
    on payments (service_category);

create index if not exists idx_idempotency_expires_at
    on idempotency_keys (expires_at);

create index if not exists idx_payment_history_payment
    on payment_status_history (payment_id, changed_at);

create index if not exists idx_provider_responses_payment
    on provider_responses (payment_id, received_at desc);

create index if not exists idx_attempts_payment
    on payment_attempts (payment_id, attempt_number);

create index if not exists idx_audit_payment_created
    on audit_log (payment_id, created_at desc);

create index if not exists idx_requisites_payment
    on payment_requisites (payment_id);
