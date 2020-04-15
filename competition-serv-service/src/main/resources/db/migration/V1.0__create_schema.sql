CREATE SCHEMA IF NOT EXISTS compservice;

drop table if exists compservice.competition_properties cascade;

drop table if exists compservice.category_restriction cascade;

drop table if exists compservice.event cascade;

drop table if exists compservice.mat_description cascade;

drop table if exists compservice.registration_info cascade;

drop table if exists compservice.competition_properties_staff_ids cascade;

drop table if exists compservice.promo_code cascade;

drop table if exists compservice.competitor cascade;

drop table if exists compservice.registration_period cascade;

drop table if exists compservice.registration_group cascade;

drop table if exists compservice.reg_group_reg_period cascade;

drop table if exists compservice.schedule_period cascade;

drop table if exists compservice.category_descriptor_restriction cascade;

drop table if exists compservice.competitor_categories cascade;

drop table if exists compservice.schedule_entry cascade;

drop table if exists compservice.stage_descriptor cascade;

drop table if exists compservice.fight_description cascade;

drop table if exists compservice.comp_score cascade;

drop table if exists compservice.fight_start_times cascade;

drop table if exists compservice.fight_result_option cascade;

drop table if exists compservice.stage_input_descriptor cascade;

drop table if exists compservice.registration_group_categories cascade;

drop table if exists compservice.competitor_selector cascade;

drop table if exists compservice.competitor_selector_selector_value cascade;

drop table if exists compservice.competitor_stage_result cascade;

create table compservice.category_restriction
(
    id        varchar(255) not null
        constraint category_restriction_pkey
            primary key,
    max_value varchar(255),
    min_value varchar(255),
    name      varchar(255),
    type      integer,
    value     varchar(255),
    alias     varchar(255),
    unit      varchar(255)
);

create table compservice.competition_properties
(
    id                          varchar(255) not null
        constraint competition_properties_pkey
            primary key,
    brackets_published          boolean      not null,
    competition_name            varchar(255) not null,
    creation_timestamp          bigint       not null,
    creator_id                  varchar(255) not null,
    email_notifications_enabled boolean,
    email_template              varchar(255),
    end_date                    timestamp    not null,
    schedule_published          boolean      not null,
    start_date                  timestamp    not null,
    time_zone                   varchar(255) not null,
    status                      integer      not null,
    competition_image           oid,
    competition_info_template   oid
);


create table compservice.event
(
    id             varchar(255) not null
        constraint event_pkey
            primary key,
    category_id    varchar(255),
    competition_id varchar(255),
    correlation_id varchar(255),
    mat_id         varchar(255),
    payload        text,
    type           integer
);


create table compservice.registration_info
(
    id                varchar(255) not null
        constraint registration_info_pkey
            primary key
        constraint fkkn1g93oujod8w0b40ojdj16cy
            references compservice.competition_properties on delete cascade,
    registration_open boolean      not null
);

create table compservice.competition_properties_staff_ids
(
    competition_properties_id varchar(255) not null
        constraint fkryyutsan2yax6j6kts8xdqmwf
            references compservice.competition_properties on delete cascade,
    staff_id                  varchar(255) not null,
    constraint competition_properties_staff_ids_pkey
        primary key (competition_properties_id, staff_id)

);

create table compservice.promo_code
(
    id             varchar(255) not null
        constraint promo_code_pkey
            primary key,
    coefficient    numeric(19, 2),
    competition_id varchar(255)
        constraint fk93bww360lp5pakmmaciweqpb1
            references compservice.competition_properties on delete cascade,
    expire_at      timestamp,
    start_at       timestamp
);

create table compservice.competitor
(
    id                  varchar(255) not null
        constraint competitor_pkey
            primary key,
    academy_id          varchar(255),
    academy_name        varchar(255),
    birth_date          timestamp,
    competition_id      varchar(255)
        constraint competitor_competition_id_fkey
            references compservice.competition_properties on delete cascade,
    email               varchar(255),
    first_name          varchar(255),
    last_name           varchar(255),
    promo               varchar(255)
        constraint competitor_promo_code_fkey
            references compservice.promo_code,
    registration_status integer,
    user_id             varchar(255)
);

create table compservice.registration_period
(
    id                   varchar(255) not null
        constraint registration_period_pkey
            primary key,
    end_date             timestamp,
    name                 varchar(255),
    start_date           timestamp,
    registration_info_id varchar(255) not null
        constraint fk45qkmmy3u7pp3chdinw2y5pxh
            references compservice.registration_info on delete cascade
);

create table compservice.registration_group
(
    id                   varchar(255) not null
        constraint registration_group_pkey
            primary key,
    default_group        boolean      not null,
    display_name         varchar(255),
    registration_fee     numeric(19, 2),
    registration_info_id varchar(255) not null
        constraint fk4e6y1qlhaqfsdk5no3e21uask
            references compservice.registration_info on delete cascade
);

create table compservice.reg_group_reg_period
(
    reg_group_id  varchar(255) not null
        constraint fkfhwupl1py85g0cyjikvp794hi
            references compservice.registration_period on delete cascade,
    reg_period_id varchar(255) not null
        constraint fkn4hr8mvec1fixuba1wmv1271r
            references compservice.registration_group on delete cascade,
    constraint reg_group_reg_period_pkey
        primary key (reg_group_id, reg_period_id)
);


create table compservice.schedule_period
(
    id                  varchar(255) not null
        constraint schedule_period_pkey
            primary key,
    name                varchar(255),
    is_active           boolean,
    start_time          timestamp,
    risk_percent        numeric(19, 2),
    time_between_fights integer      not null,
    end_time            timestamp,
    competition_id      varchar(255)
        constraint fkeai1ckn6fv7x90xkah9s48faa
            references compservice.competition_properties on delete cascade
);

create table compservice.mat_description
(
    id        varchar(255) not null
        constraint mat_description_pkey
            primary key,
    name      varchar(255),
    mat_order integer,
    period_id varchar(255) not null
        constraint fkheux8852yfm9g3iwegpqr8sbe
            references compservice.schedule_period on delete cascade
);

create table compservice.category_descriptor
(
    id                varchar(255) not null
        constraint category_descriptor_pkey
            primary key,
    competition_id    varchar(255),
    fight_duration    numeric(19, 2),
    name              varchar(255),
    registration_open boolean      not null
);

create table compservice.category_descriptor_restriction
(
    category_descriptor_id  varchar(255) not null
        constraint fkk8wjeh0jor53tmgwqf9ksnvh
            references compservice.category_descriptor on delete cascade,
    category_restriction_id varchar(255) not null
        constraint fkkrfbh806v4qi47vg3fatmcol0
            references compservice.category_restriction on delete cascade,
    constraint category_descriptor_restriction_pkey
        primary key (category_descriptor_id, category_restriction_id)
);

create table compservice.competitor_categories
(
    competitors_id varchar(255) not null
        constraint fk8mn514i4vj9gafgmqdss7vwks
            references compservice.competitor on delete cascade,
    categories_id  varchar(255) not null
        constraint fkmojk2a016rw6htwed62g1oypf
            references compservice.category_descriptor on delete cascade,
    constraint competitor_categories_pkey
        primary key (competitors_id, categories_id)
);

create table compservice.stage_descriptor
(
    id                      varchar(255) not null
        constraint stage_descriptor_pkey
            primary key,
    category_id             varchar(255)
        constraint stage_descriptor_category_id_fkey
            references compservice.category_descriptor on delete cascade,
    bracket_type            integer,
    competition_id          varchar(255),
    name                    varchar(255),
    stage_order             integer,
    stage_status            integer,
    stage_type              integer,
    wait_for_previous       boolean,
    has_third_place_fight   boolean,
    force_manual_assignment boolean,
    output_size             integer
);

create table compservice.additional_group_sorting_descriptor
(
    stage_id             varchar(255) not null
        constraint additional_group_sorting_descriptor_stage_id_fkey references compservice.stage_descriptor on delete cascade,
    group_sort_direction integer,
    group_sort_specifier integer      not null,
    constraint additional_group_sorting_descriptor_pkey
        primary key (stage_id, group_sort_specifier)
);

create table compservice.group_descriptor
(
    id       varchar(255) not null
        constraint group_descriptor_pkey
            primary key,
    stage_id varchar(255)
        constraint group_descriptor_stage_descriptor_id_fkey
            references compservice.stage_descriptor on delete cascade,
    name     varchar(255),
    size     integer      not null
);

create table compservice.fight_result_option
(
    id                       varchar(255)   not null
        constraint fight_result_option_pkey
            primary key,
    winner_additional_points numeric(19, 2),
    loser_additional_points  numeric(19, 2),
    description              varchar(255),
    short_name               varchar(255)   not null,
    draw                     boolean,
    winner_points            numeric(19, 2) not null,
    loser_points             numeric(19, 2) not null,
    stage_id                 varchar(255)
        constraint fkhj7y0idxgjgbg8b4el2qr8nc6
            references compservice.stage_descriptor on delete cascade
);

create table compservice.schedule_entry
(
    id             varchar(255) not null
        constraint schedule_entry_pkey
            primary key,
    period_id      varchar(255) not null
        constraint fkafy2hinwcl6a1ugke2uctic0w
            references compservice.schedule_period on delete cascade,
    duration       numeric(19, 2),
    entry_type     integer      not null,
    start_time     timestamp    not null,
    end_time       timestamp,
    schedule_order integer      not null,
    description    varchar(255),
    name           varchar(255),
    color          varchar(255),
    constraint schedule_unique_period_mat_order
        unique (period_id, schedule_order, entry_type)
);

create table compservice.schedule_requirement
(
    id               varchar(255) not null
        constraint schedule_requirement_pkey
            primary key,
    period_id        varchar(255) not null
        constraint schedule_requirement_period_id_fkey
            references compservice.schedule_period on delete cascade,
    mat_id           varchar(255)
        constraint schedule_requirement_mat_id_fkey
            references compservice.mat_description,
    entry_type       integer      not null,
    entry_order      integer      not null,
    start_time       timestamp,
    force            boolean,
    end_time         timestamp,
    duration_minutes numeric(19, 2),
    description      varchar(255),
    name             varchar(255),
    color            varchar(255),
    constraint schedule_requirement_unique_period_mat_order
        unique (period_id, mat_id, entry_order)
);

create table compservice.fight_description
(
    id                      varchar(255) not null
        constraint fight_description_pkey
            primary key,
    category_id             varchar(255)
        constraint fight_description_category_id_fkey
            references compservice.category_descriptor on delete cascade,
    competition_id          varchar(255)
        constraint fight_description_competition_id_fkey
            references compservice.competition_properties on delete cascade,
    duration                numeric(19, 2),
    fight_name              varchar(255),
    winner_id               varchar(255)
        constraint fight_description_winner_id_fkey
            references compservice.competitor,
    reason                  varchar(255),
    result_type             varchar(255)
        constraint fight_description_fight_output_fkey references compservice.fight_result_option,
    lose_fight              varchar(255),
    mat_id                  varchar(255)
        constraint fight_description_mat_description_fkey
            references compservice.mat_description,
    number_in_round         integer      not null,
    number_on_mat           integer,
    parent_1_fight_id       varchar(255),
    parent_1_reference_type integer,
    parent_2_fight_id       varchar(255),
    parent_2_reference_type integer,
    period                  varchar(255)
        constraint fight_description_schedule_period
            references compservice.schedule_period,
    priority                integer      not null,
    round                   integer,
    round_type              integer,
    status                  integer      not null,
    start_time              timestamp,
    win_fight               varchar(255),
    stage_id                varchar(255)
        constraint fk83j4njug11q161thma55h5b6a
            references compservice.stage_descriptor on delete cascade,
    group_id                varchar(255)
        constraint fight_description_group_descriptor_fkey
            references compservice.group_descriptor on delete cascade,
    invalid                 boolean,
    schedule_entry_id       varchar(255)
        constraint fight_description_schedule_entry_fkey
            references compservice.schedule_entry
);


create table compservice.schedule_requirement_fight_description
(
    requirement_id varchar(255) not null
        constraint schedule_requirement_fight_description_requirement_id references compservice.schedule_requirement on delete cascade,
    fight_id       varchar(255) not null
        constraint schedule_requirement_fight_description_fight_id references compservice.fight_description on delete cascade,
    constraint schedule_requirement_fight_description_pkey
        primary key (requirement_id, fight_id)
);

create table compservice.schedule_requirement_category_description
(
    requirement_id varchar(255) not null
        constraint schedule_requirement_category_description_requirement_id references compservice.schedule_requirement on delete cascade,
    category_id    varchar(255) not null
        constraint schedule_requirement_category_description_category_id references compservice.category_descriptor on delete cascade,
    constraint schedule_requirement_category_description_pkey
        primary key (requirement_id, category_id)
);



create table compservice.category_schedule_entry
(
    category_id       varchar(255) not null
        constraint category_schedule_entry_category_fkey references compservice.category_descriptor on delete cascade,
    schedule_entry_id varchar(255) not null
        constraint category_schedule_entry_schedule_entry_fkey references compservice.schedule_entry on delete cascade,
    constraint category_schedule_entry_pkey primary key (category_id, schedule_entry_id)
);

create table compservice.schedule_entry_schedule_requirement
(
    schedule_requirement_id varchar(255) not null
        constraint schedule_entry_schedule_requirement_schedule_requirement_fkey references compservice.schedule_requirement on delete cascade,
    schedule_entry_id       varchar(255) not null
        constraint schedule_entry_schedule_requirement_schedule_entry_fkey references compservice.schedule_entry on delete cascade,
    constraint schedule_entry_schedule_requirement_pkey primary key (schedule_requirement_id, schedule_entry_id)
);



create table compservice.comp_score
(
    advantages                     integer,
    penalties                      integer,
    points                         integer,
    placeholder_id                 varchar(255),
    compscore_competitor_id        varchar(255)
        constraint fkiuy2929idw7lx296op7w8govx
            references compservice.competitor on delete cascade,
    compscore_fight_description_id varchar(255) not null
        constraint fk5jdsq3wdtfltdvjb7hmmqu497
            references compservice.fight_description on delete cascade,
    comp_score_order               integer,
    constraint comp_score_pkey
        primary key (comp_score_order, compscore_fight_description_id)
);

create table compservice.stage_input_descriptor
(
    id                    varchar(255) not null
        constraint stage_input_descriptor_pkey
            primary key
        constraint stage_input_fk_stage_descriptor
            references compservice.stage_descriptor on delete cascade,
    distribution_type     integer,
    number_of_competitors integer      not null
);

create table compservice.registration_group_categories
(
    registration_group_id varchar(255) not null
        constraint fklcnvc1xtt6gfrwixoj0mvqchg
            references compservice.registration_group on delete cascade,
    category_id           varchar(255) not null
        constraint reg_group_category_id_fkey
            references compservice.category_descriptor on delete cascade,
    constraint registration_group_categories_pkey
        primary key (registration_group_id, category_id)
);

create table compservice.competitor_selector
(
    id                varchar(255) not null
        constraint competitor_selector_pkey
            primary key,
    apply_to_stage_id varchar(255),
    classifier        integer,
    logical_operator  integer,
    operator          integer,
    stage_input_id    varchar(255)
        constraint fkhn69xal0k08it5niovv40cr6l
            references compservice.stage_input_descriptor on delete cascade
);

create table compservice.competitor_selector_selector_value
(
    competitor_selector_id varchar(255) not null
        constraint fk1s0fmnd8kgdabvp3qml62yee2
            references compservice.competitor_selector on delete cascade,
    selector_value         varchar(255) not null,
    constraint competitor_selector_selector_value_pkey
        primary key (competitor_selector_id, selector_value)
);

create table compservice.competitor_stage_result
(
    group_id      varchar(255),
    conflicting   boolean,
    place         integer      not null,
    points        numeric(19, 2),
    round_type    integer,
    round         integer,
    stage_id      varchar(255) not null
        constraint competitor_stage_result
            references compservice.stage_descriptor on delete cascade,
    competitor_id varchar(255) not null
        constraint fk9axdhjm23nfsx1xuofkmq5cyo
            references compservice.competitor on delete cascade,
    constraint competitor_stage_result_pkey
        primary key (stage_id, competitor_id)
);
