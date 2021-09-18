DROP KEYSPACE IF EXISTS query_service;
CREATE KEYSPACE if not exists query_service with replication = {'class':'SimpleStrategy', 'replication_factor':1};

CREATE TABLE if not exists query_service.competition_properties
(
    id                 varchar,
    creator_id         varchar,
    staff_ids          set<varchar>,
    competition_name   varchar,
    template           blob,
    start_date         timestamp,
    schedule_published Boolean,
    brackets_published Boolean,
    end_date           timestamp,
    time_zone          varchar,
    registration_open  Boolean,
    creation_timestamp timestamp,
    status             varchar,
    primary key ( id, creation_timestamp, creator_id, status, start_date )

);

CREATE TYPE if not exists query_service.restriction (
    restriction_id uuid,
    restriction_type varchar,
    name varchar,
    value varchar,
    alias varchar,
    min_value varchar,
    max_value varchar,
    unit varchar,
    restriction_order int
    );

CREATE TABLE if not exists query_service.category
(
    id                varchar,
    competition_id    varchar,
    restrictions      list<frozen<query_service.restriction>>,
    name              varchar,
    registration_open boolean,
    primary key ( competition_id, id )
);

create type if not exists query_service.academy(
    academy_id varchar,
    academy_name varchar,
    );

create table if not exists query_service.competitor
(
    competition_id varchar,
    user_id        varchar,
    email          varchar,
    id             varchar,
    first_name     varchar,
    last_name      varchar,
    birth_date     timestamp,
    academy        query_service.academy,
    categories     set<varchar>,
    is_placeholder boolean,
    promo          varchar,
    primary key ( competition_id, id )
);

create type if not exists query_service.mat (
    mat_id varchar,
    name varchar,
    mat_order int
    );

create type if not exists query_service.schedule_info (
    mat frozen<mat>,
    number_on_mat int,
    period_id varchar,
    start_time timestamp,
    invalid boolean,
    schedule_entry_id varchar
    );

create type if not exists query_service.brackets_info (
    number_in_round int,
    win_fight varchar,
    lose_fight varchar,
    round_type varchar
    );
create type if not exists query_service.point_group (
    point_group_id varchar,
    name varchar,
    priority int,
    value int
    );

create type if not exists query_service.score (
    points Int,
    advantages int,
    penalties int,
    point_groups list<frozen<query_service.point_group>>
    );

create type if not exists query_service.comp_score (
    placeholder_id varchar,
    competitor_id varchar,
    score frozen<score>,
    comp_score_order Int,
    parent_reference_type varchar,
    parent_fight_id varchar
    );

create type if not exists query_service.fight_result (
    winner_id varchar,
    result_type_id varchar,
    reason varchar );
create type if not exists query_service.mat_id_and_some_id (
    mat_id varchar,
    some_id varchar,
    start_time timestamp );

create table if not exists query_service.fight
(

    id             varchar,
    competition_id varchar,
    category_id    varchar,
    stage_id       varchar,
    schedule_info  query_service.schedule_info,
    brackets_info  query_service.brackets_info,
    fight_result   query_service.fight_result,
    scores         list<frozen<query_service.comp_score>>,
    primary key ( competition_id, id, stage_id )
);

CREATE TABLE if not exists query_service.managed_competition
(
    competition_id varchar,
    events_topic   varchar,
    creator_id     varchar,
    created_at     timestamp,
    starts_at      timestamp,
    ends_at        timestamp,
    time_zone      varchar,
    status         varchar,
    primary key ( competition_id, created_at, creator_id, starts_at, status )
);

create type if not exists query_service.schedule_entry(
    entry_id varchar,
    competition_id varchar,
    category_ids set<varchar>,
    fight_ids list<frozen<query_service.mat_id_and_some_id>>,
    period_id varchar,
    description varchar,
    name varchar,
    color varchar,
    entry_type varchar,
    requirement_ids set<varchar>,
    start_time timestamp,
    end_time timestamp,
    number_of_fights Int,
    entry_duration Int,
    entry_order Int
    );

create type if not exists query_service.schedule_requirement(
    entry_id varchar,
    competition_id varchar,
    category_ids set<varchar>,
    fight_ids set<varchar>,
    mat_id varchar,
    period_id varchar,
    name varchar,
    color varchar,
    entry_type varchar,
    force Boolean,
    start_time timestamp,
    end_time timestamp,
    duration_seconds Int,
    entry_order Int
    );
create type if not exists query_service.registration_fee(
    currency varchar,
    amount Int,
    remainder Int
    );

create table if not exists query_service.period
(
    competition_id        varchar,
    name                  varchar,
    id                    varchar,
    mats                  list<frozen<query_service.mat>>,
    start_time            timestamp,
    end_time              timestamp,
    active                Boolean,
    time_between_fights   Int,
    risk_coefficient      Int,
    schedule_entries      list<frozen<query_service.schedule_entry>>,
    schedule_requirements list<frozen<query_service.schedule_requirement>>,
    primary key ( competition_id, id )
);

create table if not exists query_service.registration_group
(
    competition_id   varchar,
    id               varchar,
    is_default_group Boolean,
    registrationFee  query_service.registration_fee,
    categories       set<varchar>,
    primary key ( competition_id, id )
);

create table if not exists query_service.registration_period
(
    competition_id         varchar,
    id                     varchar,
    name                   varchar,
    start                  timestamp,
    end                    timestamp,
    registration_group_ids set<varchar>,
    primary key ( competition_id, id )
);
create type if not exists query_service.group_descriptor(
    group_id varchar,
    name varchar,
    size Int
    );

create type if not exists query_service.competitor_selector (
    selector_id varchar,
    apply_to_stage_id varchar,
    logical_operator varchar,
    classifier varchar,
    operator varchar,
    selector_value frozen<set<varchar>>
    );

create type if not exists query_service.stage_input_descriptor(
    descriptor_id varchar,
    number_of_competitors Int,
    selectors frozen<competitor_selector>,
    distribution_type varchar
    );

create type if not exists query_service.fight_result_option(
    option_id varchar,
    description varchar,
    short_name varchar,
    draw Boolean,
    winner_points Int,
    winner_additional_points Int,
    loser_points Int,
    loser_additional_points Int
    );

create type if not exists query_service.competitor_stage_result(
    competitor_id varchar,
    points Int,
    round Int,
    round_type varchar,
    place Int,
    stage_id varchar,
    group_id varchar,
    conflicting Boolean
    );

create type if not exists query_service.additional_group_sorting_descriptor(
    group_sort_direction varchar,
    group_sort_specifier varchar
    );

create type if not exists query_service.stage_result_descriptor(
    descrioptor_id varchar,
    name varchar,
    force_manual_assignment Boolean,
    output_size Int,
    fight_result_options frozen<list<frozen<query_service.fight_result_option>>>,
    competitor_results frozen<list<frozen<query_service.competitor_stage_result>>>,
    additional_group_sorting_descriptors frozen<list<frozen<query_service.additional_group_sorting_descriptor>>>
    );

create table if not exists query_service.stage_descriptor
(
    id                      varchar,
    name                    varchar,
    category_id             varchar,
    competition_id          varchar,
    bracket_type            varchar,
    stage_type              varchar,
    stage_status            varchar,
    stage_result_descriptor query_service.stage_result_descriptor,
    input_descriptor        query_service.stage_input_descriptor,
    stage_order             Int,
    wait_for_previous       Boolean,
    has_third_place_fight   Boolean,
    group_descriptors       list<frozen<query_service.group_descriptor>>,
    number_of_fights        Int,
    fight_duration          bigint,
    primary key ( competition_id, category_id, id )
);


