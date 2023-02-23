drop table if exists public.journal;

drop table if exists public.snapshot;

create table if not exists public.event_journal
(
    ordering bigserial, --noqa: disable=L029
    persistence_id varchar(255) not null,
    sequence_number bigint not null,
    deleted boolean default false not null,

    writer varchar(255) not null,
    write_timestamp bigint,
    adapter_manifest varchar(255),

    event_ser_id integer not null,
    event_ser_manifest varchar(255) not null,
    event_payload bytea not null,

    meta_ser_id integer,
    meta_ser_manifest varchar(255),
    meta_payload bytea,

    primary key (persistence_id, sequence_number)
);

CREATE UNIQUE INDEX event_journal_ordering_idx ON public.event_journal (ordering); -- noqa: PRS

create table if not exists public.event_tag
(
    event_id bigint,
    tag varchar(256),
    primary key (event_id, tag),
    constraint fk_event_journal
    foreign key (event_id)
    references event_journal (ordering)
    on delete cascade
);

create table if not exists public.snapshot
(
    persistence_id varchar(255) not null,
    sequence_number bigint not null,
    created bigint not null,

    snapshot_ser_id integer not null,
    snapshot_ser_manifest varchar(255) not null,
    snapshot_payload bytea not null,

    meta_ser_id integer,
    meta_ser_manifest varchar(255),
    meta_payload bytea,

    primary key (persistence_id, sequence_number)
);
