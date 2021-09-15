CREATE KEYSPACE query_service with replication = {'class':'SimpleStrategy', 'replication_factor':1};
CREATE TYPE if not exists competition_info_template (
    template blob
    );
CREATE TABLE if not exists competition_properties
(
    id                 uuid,
    creator_id         varchar,
    staff_ids          set<frozen<varchar>>,
    competition_name   varchar,
    info_template      competition_info_template,
    start_date         timestamp,
    schedule_published Boolean,
    brackets_published Boolean,
    end_date           timestamp,
    time_zone          varchar,
    registration_open  Boolean,
    creation_timestamp timestamp,
    status             varchar
);

CREATE TYPE if not exists restriction (
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

CREATE TABLE if not exists category
(
    id                varchar,
    competition_id    varchar,
    restrictions      list<frozen<restriction>>,
    name              varchar,
    registration_open boolean
);

create type if not exists academy(
    academy_id varchar,
    academy_name varchar,
    );

create table if not exists competitor
(
    competition_id varchar,
    user_id        varchar,
    email          varchar,
    id             varchar,
    first_name     varchar,
    last_name      varchar,
    birth_date     timestamp,
    academy        academy,
    categories     set<frozen<varchar>>,
    is_placeholder boolean,
    promo          varchar
);

create type if not exists mat (
    mat_id varchar,
    name varchar,
    mat_order int
    );

create type if not exists schedule_info (
    mat mat,
    number_on_mat int,
    period_id varchar,
    start_time timestamp,
    invalid boolean,
    schedule_entry_id varchar
    );

create type if not exists brackets_info (
    stage_id varchar,
    number_in_round int,
    win_fight varchar,
    lose_fight varchar,
    round_type varchar
    );
create type if not exists point_group (
    point_group_id varchar,
    name varchar,
    priority int,
    value int
    );

create type if not exists score (
    points Int,
    advantages int,
    penalties int,
    point_groups list<frozen<point_group>>
    );

create type if not exists comp_score (
    placeholder_id varchar,
    competitor_id varchar,
    score score,
    comp_score_order Int,
    parent_reference_type varchar,
    parent_fight_id varchar
    );

create type if not exists fight_result (
    winner_id varchar,
    result_type_id varchar,
    reason varchar );
create type if not exists mat_id_and_some_id (
    mat_id varchar,
    some_id varchar,
    start_time timestamp );

create table if not exists fight
(

    id             varchar,
    competition_id varchar,
    category_id    varchar,
    schedule_info  schedule_info,
    brackets_info  brackets_info,
    fight_result   fight_result,
    scores         list<frozen<comp_score>>
);

CREATE TABLE if not exists managed_competition
(
    competition_id varchar,
    events_topic   varchar,
    creator_id     varchar,
    created_at     timestamp,
    starts_at      timestamp,
    ends_at        timestamp,
    time_zone      varchar,
    status         varchar
);

create type if not exists schedule_entry(
    entry_id varchar,
    competition_id varchar,
    category_ids set<frozen<varchar>>,
    fight_ids list<frozen<mat_id_and_some_id>>,
    period_id varchar,
    description varchar,
    name varchar,
    color varchar,
    entry_type varchar,
    requirement_ids set<frozen<varchar>>,
    start_time timestamp,
    end_time timestamp,
    number_of_fights Int,
    entry_duration Int,
    entry_order Int
    );

create type if not exists schedule_requirement(
    entry_id varchar,
    competition_id varchar,
    category_ids set<frozen<varchar>>,
    fight_ids set<frozen<varchar>>,
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
create type if not exists registration_fee(
    currency varchar,
    amount Int,
    remainder Int
    );

create table if not exists period
(
    comppetition_id       varchar,
    name                  varchar,
    id                    varchar,
    mats                  set<frozen<mat>>,
    start_time            timestamp,
    end_time              timestamp,
    active                Boolean,
    time_between_fights   Int,
    risk_coefficient      Int,
    schedule_entries      list<frozen<schedule_entry>>,
    schedule_requirements list<frozen<schedule_requirement>>
);

create table if not exists registration_group
(
    competition_id   varchar,
    id               varchar,
    is_default_group Boolean,
    registrationFee  registration_fee,
    categories       set<frozen<varchar>>
);

create table if not exists registration_period
(
    competition_id         varchar,
    id                     varchar,
    name                   varchar,
    start                  timestamp,
    end                    timestamp,
    registration_group_ids set<frozen<varchar>>
);
create type if not exists group_descriptor(
    group_id varchar,
    name varchar,
    size Int
    );

create type if not exists competitor_selector (
    selector_id varchar,
    apply_to_stage_id varchar,
    logical_operator varchar,
    classifier varchar,
    operator varchar,
    selector_value set<frozen<varchar>>
    );

create type if not exists stage_input_descriptor(
    descriptor_id varchar,
    number_of_competitors Int,
    selectors competitor_selector,
    distribution_type varchar
    );

create type if not exists fight_result_option(
    option_id varchar,
    description varchar,
    short_name varchar,
    draw Boolean,
    winner_points Int,
    winner_additional_points Int,
    loser_points Int,
    loser_additional_points Int
    );

create type if not exists competitor_stage_result(
    competitor_id varchar,
    points Int,
    round Int,
    round_type varchar,
    place Int,
    stage_id varchar,
    group_id varchar,
    conflicting Boolean
    );

create type if not exists additional_group_sorting_descriptor(
    group_sort_direction varchar,
    group_sort_specifier varchar
    );

create type if not exists stage_result_descriptor(
    descrioptor_id varchar,
    name varchar,
    force_manual_assignment Boolean,
    output_size Int,
    fight_result_options list<frozen<fight_result_option>>,
    competitor_results list<frozen<competitor_stage_result>>,
    additional_group_sorting_descriptors list<frozen<additional_group_sorting_descriptor>>
    );

create table if not exists stage_descriptor
(
    id                      varchar,
    name                    varchar,
    category_id             varchar,
    competition_id          varchar,
    bracket_type            varchar,
    stage_type              varchar,
    stage_status            varchar,
    stage_result_descriptor stage_result_descriptor,
    input_descriptor        stage_input_descriptor,
    stage_order             Int,
    wait_for_previous       Boolean,
    has_third_place_fight   Boolean,
    group_descriptors       list<frozen<group_descriptor>>,
    number_of_fights        Int,
    fight_duration          bigint
);


