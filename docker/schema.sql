drop table IF EXISTS public.journal;

create TABLE IF NOT EXISTS public.journal (
 ordering BIGSERIAL,
 persistence_id VARCHAR(255) NOT NULL,
 sequence_number BIGINT NOT NULL,
 deleted BOOLEAN DEFAULT FALSE NOT NULL,
 tags VARCHAR(255) DEFAULT NULL,
 message BYTEA NOT NULL,
 PRIMARY KEY(persistence_id, sequence_number)
);

create UNIQUE INDEX journal_ordering_idx ON public.journal(ordering);

drop table IF EXISTS public.snapshot;

create TABLE IF NOT EXISTS public.snapshot (
 persistence_id VARCHAR(255) NOT NULL,
 sequence_number BIGINT NOT NULL,
 created BIGINT NOT NULL,
 snapshot BYTEA NOT NULL,
 PRIMARY KEY(persistence_id, sequence_number)
);


