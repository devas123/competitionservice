-- create schema public;
--
-- alter schema public owner to postgres;

create sequence hibernate_sequence;

alter sequence hibernate_sequence owner to postgres;

create table command
(
    id varchar(255) not null
        constraint command_pkey
            primary key,
    category_id varchar(255),
    competition_id varchar(255),
    correlation_id varchar(255),
    executed boolean not null,
    mat_id varchar(255),
    payload oid,
    timestamp bigint,
    type integer
);

alter table command owner to postgres;

create table command_metadata_entry
(
    command_metadata_id varchar(255) not null
        constraint fks9l7afwo7h64e0qsjf3dowa22
            references command,
    metadata_key varchar(255),
    metadata_value varchar(255)
);

alter table command_metadata_entry owner to postgres;


create table comp_scores
(
    fight_id varchar(255) not null,
    compscore_competitor_id varchar(255) not null,
    advantages integer not null,
    penalties integer not null,
    points integer not null,
    comp_score_order integer not null,
    constraint comp_scores_pkey
        primary key (fight_id, comp_score_order)
);

alter table comp_scores owner to postgres;

create table academy
(
    id varchar(255) not null
        constraint academy_pkey
            primary key,
    created bigint,
    name varchar(255)
);

alter table academy owner to postgres;

create table academy_coaches
(
    academy_id varchar(255) not null
        constraint fkdrcx6riqu7hwsb00j1rwnxub2
            references academy,
    coaches varchar(255),
    coaches_order integer not null,
    constraint academy_coaches_pkey
        primary key (academy_id, coaches_order)
);

alter table academy_coaches owner to postgres;

create table category_descriptor_competitors
(
    categories_id varchar(255) not null,
    competitors_id varchar(255) not null,
    constraint category_descriptor_competitors_pkey
        primary key (categories_id, competitors_id)
);

alter table category_descriptor_competitors owner to postgres;

create table dashboard_period_mat_ids
(
    dashboard_period_id varchar(255) not null,
    mat_ids varchar(255)
);

alter table dashboard_period_mat_ids owner to postgres;

create table bracket_descriptor
(
    id varchar(255) not null
        constraint bracket_descriptor_pkey
            primary key,
    bracket_type integer,
    competition_id varchar(255)
);

alter table bracket_descriptor owner to postgres;

create table category_restriction
(
    id varchar(255) not null
        constraint category_restriction_pkey
            primary key,
    max_value varchar(255),
    min_value varchar(255),
    name varchar(255),
    type integer,
    unit varchar(255)
);

alter table category_restriction owner to postgres;

create table competition_state
(
    id varchar(255) not null
        constraint competition_state_pkey
            primary key,
    competition_image oid,
    competition_info_template oid,
    status integer
);

alter table competition_state owner to postgres;

create table dashboard_state
(
    id varchar(255) not null
        constraint dashboard_state_pkey
            primary key
);

alter table dashboard_state owner to postgres;

create table dashboard_period
(
    id varchar(255) not null
        constraint dashboard_period_pkey
            primary key,
    is_active boolean not null,
    name varchar(255),
    start_time timestamp,
    dashboard_id varchar(255)
        constraint fkc4sl34l4xcy8wicikc8p2ku0n
            references dashboard_state
);

alter table dashboard_period owner to postgres;

create table event
(
    id varchar(255) not null
        constraint event_pkey
            primary key,
    category_id varchar(255),
    competition_id varchar(255),
    correlation_id varchar(255),
    mat_id varchar(255),
    payload text,
    type integer
);

alter table event owner to postgres;

create table mat_description
(
    id varchar(255) not null
        constraint mat_description_pkey
            primary key,
    name varchar(255),
    dashboard_period_id varchar(255) not null
        constraint fk3rv512vhq8j2k4c40g3ly5792
            references dashboard_period,
    mats_order integer
);

alter table mat_description owner to postgres;

create table registration_info
(
    id varchar(255) not null
        constraint registration_info_pkey
            primary key,
    registration_open boolean not null
);

alter table registration_info owner to postgres;

create table competition_properties
(
    id varchar(255) not null
        constraint competition_properties_pkey
            primary key
        constraint fkkn1g93oujod8w0b40ojdj16cy
            references registration_info,
    brackets_published boolean not null,
    competition_name varchar(255),
    creation_timestamp bigint not null,
    creator_id varchar(255),
    email_notifications_enabled boolean,
    email_template varchar(255),
    end_date timestamp,
    schedule_published boolean not null,
    start_date timestamp,
    time_zone varchar(255)
);

alter table competition_properties owner to postgres;

create table competition_properties_staff_ids
(
    competition_properties_id varchar(255) not null
        constraint fkryyutsan2yax6j6kts8xdqmwf
            references competition_properties,
    staff_ids varchar(255)
);

alter table competition_properties_staff_ids owner to postgres;

create table competitor
(
    id varchar(255) not null
        constraint competitor_pkey
            primary key,
    academy_id varchar(255),
    academy_name varchar(255),
    birth_date timestamp,
    competition_id varchar(255)
        constraint competitor_competition_id_fkey
            references competition_properties,
    email varchar(255),
    first_name varchar(255),
    last_name varchar(255),
    promo varchar(255),
    registration_status integer,
    user_id varchar(255)
);

alter table competitor owner to postgres;

create table promo_code
(
    id bigint not null
        constraint promo_code_pkey
            primary key,
    coefficient numeric(19,2),
    competition_id varchar(255)
        constraint fk93bww360lp5pakmmaciweqpb1
            references competition_properties,
    expire_at timestamp,
    start_at timestamp
);

alter table promo_code owner to postgres;

create table registration_period
(
    id varchar(255) not null
        constraint registration_period_pkey
            primary key,
    end_date timestamp,
    name varchar(255),
    start_date timestamp,
    registration_info_id varchar(255) not null
        constraint fk45qkmmy3u7pp3chdinw2y5pxh
            references registration_info
);

alter table registration_period owner to postgres;

create table registration_group
(
    id varchar(255) not null
        constraint registration_group_pkey
            primary key,
    default_group boolean not null,
    display_name varchar(255),
    registration_fee numeric(19,2),
    registration_info_id varchar(255) not null
        constraint fk4e6y1qlhaqfsdk5no3e21uask
            references registration_info
);

alter table registration_group owner to postgres;

create table reg_group_reg_period
(
    reg_group_id varchar(255) not null
        constraint fkfhwupl1py85g0cyjikvp794hi
            references registration_period,
    reg_period_id varchar(255) not null
        constraint fkn4hr8mvec1fixuba1wmv1271r
            references registration_group,
    constraint reg_group_reg_period_pkey
        primary key (reg_group_id, reg_period_id)
);

alter table reg_group_reg_period owner to postgres;

create table registration_group_categories
(
    registration_group_id varchar(255) not null
        constraint fklcnvc1xtt6gfrwixoj0mvqchg
            references registration_group,
    categories varchar(255)
);

alter table registration_group_categories owner to postgres;

create table schedule
(
    id varchar(255) not null
        constraint schedule_pkey
            primary key,
    properties_id varchar(255)
);

alter table schedule owner to postgres;

create table period
(
    id varchar(255) not null
        constraint period_pkey
            primary key,
    name varchar(255),
    number_of_mats integer not null,
    start_time timestamp,
    sched_id varchar(255)
        constraint fkeai1ckn6fv7x90xkah9s48faa
            references schedule
);

alter table period owner to postgres;

create table mat_schedule_container
(
    id varchar(255) not null
        constraint mat_schedule_container_pkey
            primary key,
    total_fights integer not null,
    period_id varchar(255) not null
        constraint fkheux8852yfm9g3iwegpqr8sbe
            references period
);

alter table mat_schedule_container owner to postgres;

create table period_properties
(
    id varchar(255) not null
        constraint period_properties_pkey
            primary key,
    name varchar(255),
    number_of_mats integer not null,
    risk_percent numeric(19,2),
    start_time timestamp,
    time_between_fights integer not null,
    sched_id varchar(255)
        constraint fkotxnpcqbjgdxl6c6e0ndigdku
            references schedule
);

alter table period_properties owner to postgres;

create table category_descriptor
(
    id varchar(255) not null
        constraint category_descriptor_pkey
            primary key,
    competition_id varchar(255),
    fight_duration numeric(19,2),
    name varchar(255),
    registration_open boolean not null,
    period_properties_id varchar(255)
        constraint fkmpbsg54mghn26gyse486xwwry
            references period_properties,
    period_id varchar(255)
        constraint fkkg7upfo8tok049xmxratl9umi
            references period
);

alter table category_descriptor owner to postgres;

create table category_descriptor_restriction
(
    category_descriptor_id varchar(255) not null
        constraint fkk8wjeh0jor53tmgwqf9ksnvh
            references category_descriptor,
    category_restriction_id varchar(255) not null
        constraint fkkrfbh806v4qi47vg3fatmcol0
            references category_restriction,
    constraint category_descriptor_restriction_pkey
        primary key (category_descriptor_id, category_restriction_id)
);

alter table category_descriptor_restriction owner to postgres;

create table category_state
(
    id varchar(255) not null
        constraint category_state_pkey
            primary key
        constraint fkpqj7wn900iwkf1rmoki48b49s
            references category_descriptor,
    status integer,
    competition_id varchar(255) not null
        constraint fk8th9hp2r52q396f1lxir9antu
            references competition_state
);

alter table category_state owner to postgres;

create table competitor_categories
(
    competitors_id varchar(255) not null
        constraint fk8mn514i4vj9gafgmqdss7vwks
            references competitor,
    categories_id varchar(255) not null
        constraint fkmojk2a016rw6htwed62g1oypf
            references category_descriptor,
    constraint competitor_categories_pkey
        primary key (competitors_id, categories_id)
);

alter table competitor_categories owner to postgres;

create table fight_description
(
    id varchar(255) not null
        constraint fight_description_pkey
            primary key,
    category_id varchar(255)
        constraint fight_description_category_id_fkey
            references category_descriptor,
    competition_id varchar(255)
        constraint fight_description_competition_id_fkey
            references competition_properties,
    duration bigint,
    draw boolean,
    reason varchar(255),
    winner_id varchar(255),
    lose_fight varchar(255),
    mat_id varchar(255),
    number_in_round integer not null,
    number_on_mat integer,
    parent_id1 varchar(255),
    parent_id2 varchar(255),
    period varchar(255),
    priority integer not null,
    round integer,
    stage integer,
    start_time timestamp,
    win_fight varchar(255),
    bracket_id varchar(255)
        constraint fkrgfv0m4epy75keoof9hx0nvge
            references bracket_descriptor,
    fight_order integer
);

alter table fight_description owner to postgres;

create table comp_score
(
    id varchar(255) not null
        constraint comp_score_pkey
            primary key,
    advantages integer,
    penalties integer,
    points integer,
    compscore_competitor_id varchar(255) not null
        constraint fkiuy2929idw7lx296op7w8govx
            references competitor,
    comp_score_id varchar(255)
        constraint fk5jdsq3wdtfltdvjb7hmmqu497
            references fight_description,
    comp_score_order integer
);

alter table comp_score owner to postgres;

create table fight_start_times
(
    mat_schedule_id varchar(255) not null
        constraint fk3qwq4ltq5uauo58p5e8ko6pld
            references mat_schedule_container,
    fight_id varchar(255) not null
        constraint fki3ci3hkg4wfrh8s2xwiellgbv
            references fight_description,
    fight_number integer not null,
    start_time timestamp,
    fights_order integer not null,
    constraint fight_start_times_pkey
        primary key (mat_schedule_id, fights_order)
);

alter table fight_start_times owner to postgres;

create table schedule_entries
(
    period_id varchar(255) not null
        constraint fkafy2hinwcl6a1ugke2uctic0w
            references period,
    category_id varchar(255)
        constraint schedule_entries_category_id_fkey
            references category_descriptor,
    fight_duration numeric(19,2),
    number_of_fights integer not null,
    start_time timestamp,
    schedule_order integer not null,
    constraint schedule_entries_pkey
        primary key (period_id, schedule_order)
);

alter table schedule_entries owner to postgres;

