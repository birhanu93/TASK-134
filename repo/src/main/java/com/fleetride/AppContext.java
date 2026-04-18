package com.fleetride;

import com.fleetride.domain.PricingConfig;
import com.fleetride.log.StructuredLogger;
import com.fleetride.repository.CustomerRepository;
import com.fleetride.repository.DisputeRepository;
import com.fleetride.repository.JobRunRepository;
import com.fleetride.repository.OrderRepository;
import com.fleetride.repository.PaymentRepository;
import com.fleetride.repository.ShareLinkRepository;
import com.fleetride.repository.TripRepository;
import com.fleetride.repository.UserRepository;
import com.fleetride.repository.AttachmentRepository;
import com.fleetride.repository.AuditRepository;
import com.fleetride.repository.CheckpointRepository;
import com.fleetride.repository.sqlite.Database;
import com.fleetride.repository.sqlite.SqliteAttachmentRepository;
import com.fleetride.repository.sqlite.SqliteAuditRepository;
import com.fleetride.repository.sqlite.SqliteCheckpointRepository;
import com.fleetride.repository.sqlite.SqliteCustomerRepository;
import com.fleetride.repository.sqlite.SqliteDisputeRepository;
import com.fleetride.repository.sqlite.SqliteJobRunRepository;
import com.fleetride.repository.sqlite.SqliteOrderRepository;
import com.fleetride.repository.sqlite.SqlitePaymentRepository;
import com.fleetride.repository.sqlite.SqliteShareLinkRepository;
import com.fleetride.repository.sqlite.SqliteTripRepository;
import com.fleetride.repository.sqlite.SqliteConfigRepository;
import com.fleetride.repository.sqlite.SqliteCouponRepository;
import com.fleetride.repository.sqlite.SqliteInvoiceRepository;
import com.fleetride.repository.sqlite.SqliteSubsidyRepository;
import com.fleetride.repository.sqlite.SqliteUpdateHistoryRepository;
import com.fleetride.repository.sqlite.SqliteUserRepository;
import com.fleetride.repository.ConfigRepository;
import com.fleetride.repository.CouponRepository;
import com.fleetride.repository.InvoiceRepository;
import com.fleetride.repository.SubsidyRepository;
import com.fleetride.repository.UpdateHistoryRepository;
import com.fleetride.security.Authorizer;
import com.fleetride.security.SecuredAttachmentService;
import com.fleetride.security.SecuredConfigService;
import com.fleetride.security.SecuredCouponService;
import com.fleetride.security.SecuredCustomerService;
import com.fleetride.security.SecuredInvoiceService;
import com.fleetride.security.SecuredSubsidyService;
import com.fleetride.security.SecuredOrderService;
import com.fleetride.security.SecuredPaymentService;
import com.fleetride.security.SecuredAuditService;
import com.fleetride.security.SecuredReconciliationService;
import com.fleetride.security.SecuredSchedulerService;
import com.fleetride.security.SecuredTripService;
import com.fleetride.security.SecuredUpdateService;
import com.fleetride.service.AttachmentService;
import com.fleetride.service.AuditService;
import com.fleetride.service.AuthService;
import com.fleetride.service.CheckpointRecoveryService;
import com.fleetride.service.CheckpointService;
import com.fleetride.service.Clock;
import com.fleetride.service.ConfigService;
import com.fleetride.service.CouponService;
import com.fleetride.service.CustomerService;
import com.fleetride.service.EncryptionService;
import com.fleetride.service.HealthService;
import com.fleetride.service.IdGenerator;
import com.fleetride.service.InvoiceService;
import com.fleetride.service.MachineIdProvider;
import com.fleetride.service.OrderService;
import com.fleetride.service.OrderStateMachine;
import com.fleetride.service.PaymentService;
import com.fleetride.service.PricingEngine;
import com.fleetride.service.ReconciliationService;
import com.fleetride.service.ScheduledJobService;
import com.fleetride.service.ShareLinkService;
import com.fleetride.service.SignatureVerifier;
import com.fleetride.service.SubsidyService;
import com.fleetride.service.TripService;
import com.fleetride.service.UpdateService;

import java.io.Closeable;
import java.nio.file.Path;

public final class AppContext implements Closeable {
    private final Database db;
    private final Clock clock;
    private final IdGenerator ids;
    private final EncryptionService encryption;

    public final PricingConfig pricingConfig;
    public final PricingEngine pricingEngine;
    public final OrderStateMachine fsm;
    public final AuditRepository auditRepo;
    public final OrderRepository orderRepo;
    public final CustomerRepository customerRepo;
    public final PaymentRepository paymentRepo;
    public final AttachmentRepository attachmentRepo;
    public final DisputeRepository disputeRepo;
    public final ShareLinkRepository shareLinkRepo;
    public final UserRepository userRepo;
    public final CheckpointRepository checkpointRepo;
    public final JobRunRepository jobRunRepo;
    public final UpdateHistoryRepository updateHistoryRepo;
    public final ConfigRepository configRepo;
    public final InvoiceRepository invoiceRepo;
    public final CouponRepository couponRepo;
    public final SubsidyRepository subsidyRepo;
    public final TripRepository tripRepo;

    public final AuditService audit;
    public final CheckpointService checkpoints;
    public final AuthService auth;
    public final Authorizer authz;
    public final CustomerService customerService;
    public final OrderService orderService;
    public final PaymentService paymentService;
    public final ReconciliationService reconciliationService;
    public final AttachmentService attachmentService;
    public final ShareLinkService shareLinkService;
    public final ScheduledJobService scheduler;
    public final ConfigService configService;
    public final HealthService healthService;
    public final UpdateService updateService;
    public final InvoiceService invoiceService;
    public final SecuredInvoiceService securedInvoiceService;
    public final CouponService couponService;
    public final SubsidyService subsidyService;
    public final SecuredCouponService securedCouponService;
    public final SecuredSubsidyService securedSubsidyService;
    public final CheckpointRecoveryService checkpointRecovery;

    public final SecuredOrderService securedOrderService;
    public final SecuredCustomerService securedCustomerService;
    public final SecuredPaymentService securedPaymentService;
    public final SecuredReconciliationService securedReconciliationService;
    public final SecuredAttachmentService securedAttachmentService;
    public final SecuredConfigService securedConfigService;
    public final SecuredUpdateService securedUpdateService;
    public final SecuredAuditService securedAuditService;
    public final SecuredSchedulerService securedSchedulerService;
    public final TripService tripService;
    public final SecuredTripService securedTripService;

    public final StructuredLogger log;
    public final MachineIdProvider machineIdProvider;

    public AppContext(Config config) {
        this.db = config.database;
        this.clock = config.clock;
        this.ids = config.ids;
        this.encryption = new EncryptionService(config.masterKey);

        this.auditRepo = new SqliteAuditRepository(db);
        this.orderRepo = new SqliteOrderRepository(db);
        this.customerRepo = new SqliteCustomerRepository(db);
        this.paymentRepo = new SqlitePaymentRepository(db);
        this.attachmentRepo = new SqliteAttachmentRepository(db);
        this.disputeRepo = new SqliteDisputeRepository(db);
        this.shareLinkRepo = new SqliteShareLinkRepository(db);
        this.userRepo = new SqliteUserRepository(db);
        this.checkpointRepo = new SqliteCheckpointRepository(db);
        this.jobRunRepo = new SqliteJobRunRepository(db);
        this.updateHistoryRepo = new SqliteUpdateHistoryRepository(db);
        this.configRepo = new SqliteConfigRepository(db);
        this.invoiceRepo = new SqliteInvoiceRepository(db);
        this.couponRepo = new SqliteCouponRepository(db);
        this.subsidyRepo = new SqliteSubsidyRepository(db);
        this.tripRepo = new SqliteTripRepository(db);

        this.pricingConfig = new PricingConfig();
        this.pricingEngine = new PricingEngine(pricingConfig);
        this.fsm = new OrderStateMachine();

        this.audit = new AuditService(auditRepo, ids, clock);
        this.checkpoints = new CheckpointService(checkpointRepo, clock);
        this.auth = new AuthService(userRepo, encryption, ids);
        this.authz = new Authorizer(auth);

        this.customerService = new CustomerService(customerRepo, encryption, ids);
        this.orderService = new OrderService(orderRepo, customerRepo, disputeRepo,
                pricingEngine, fsm, ids, clock, audit, checkpoints);
        this.paymentService = new PaymentService(paymentRepo, orderRepo, ids, clock, audit);
        this.reconciliationService = new ReconciliationService(orderRepo, paymentRepo, paymentService);
        this.attachmentService = new AttachmentService(attachmentRepo, config.attachmentDir, ids, clock);
        this.machineIdProvider = new MachineIdProvider(config.machineIdFile);
        this.shareLinkService = new ShareLinkService(shareLinkRepo, clock, machineIdProvider);
        this.scheduler = new ScheduledJobService(orderService, paymentService, customerRepo,
                audit, clock, ids, jobRunRepo, orderRepo, pricingConfig);
        this.configService = new ConfigService(pricingConfig, configRepo);
        this.healthService = new HealthService(db::ping, clock);
        this.updateService = new UpdateService(config.updateRoot, config.signatureVerifier,
                updateHistoryRepo, clock);
        this.invoiceService = new InvoiceService(invoiceRepo, orderRepo, paymentService,
                ids, clock, audit);
        this.couponService = new CouponService(couponRepo, audit);
        this.subsidyService = new SubsidyService(subsidyRepo, audit);
        orderService.attachLookupServices(couponService, subsidyService);
        this.checkpointRecovery = new CheckpointRecoveryService(checkpointRepo, checkpoints,
                orderRepo, audit);

        this.securedOrderService = new SecuredOrderService(orderService, authz, orderRepo, customerRepo);
        this.securedCustomerService = new SecuredCustomerService(customerService, authz, customerRepo);
        this.securedPaymentService = new SecuredPaymentService(paymentService, authz);
        this.securedReconciliationService = new SecuredReconciliationService(reconciliationService, authz);
        this.securedAttachmentService = new SecuredAttachmentService(attachmentService, authz,
                shareLinkService, orderRepo);
        this.securedConfigService = new SecuredConfigService(configService, authz);
        this.securedInvoiceService = new SecuredInvoiceService(invoiceService, authz);
        this.securedCouponService = new SecuredCouponService(couponService, authz);
        this.securedSubsidyService = new SecuredSubsidyService(subsidyService, authz);
        this.securedUpdateService = new SecuredUpdateService(updateService, authz,
                this::refreshActivePayloadAssets);
        this.securedAuditService = new SecuredAuditService(audit, authz);
        this.securedSchedulerService = new SecuredSchedulerService(scheduler, authz);
        this.tripService = new TripService(tripRepo, orderRepo, orderService, ids, clock, audit);
        this.securedTripService = new SecuredTripService(tripService, authz, tripRepo, customerRepo, orderRepo);

        this.log = new StructuredLogger(config.logFile, clock);
    }

    public Database database() { return db; }
    public Clock clock() { return clock; }
    public IdGenerator ids() { return ids; }
    public EncryptionService encryption() { return encryption; }

    public CheckpointRecoveryService.RecoveryReport recoverPendingCheckpoints() {
        return checkpointRecovery.recover();
    }

    /**
     * Re-extracts the currently-recorded active update payload into its versioned
     * directory so the app genuinely runs on the last imported payload even if a
     * previous shutdown killed the process between apply() and activation. The
     * refreshed payload is then wired into the runtime by reloading the ConfigService
     * overlay so template/dictionary reads reflect the active version.
     */
    public java.util.Optional<Path> activateCurrentUpdate() {
        java.util.Optional<Path> root = updateService.activate();
        refreshActivePayloadAssets();
        return root;
    }

    /**
     * Re-read runtime assets (templates, dictionaries) from whichever update payload
     * is currently active on disk. Called after startup activation and after every
     * successful apply/rollback via {@link SecuredUpdateService}'s post-activation
     * hook. Passing no active payload clears the overlay so the app reverts to
     * DB-authored values.
     */
    public synchronized void refreshActivePayloadAssets() {
        java.util.Optional<Path> root = updateService.activePayloadRoot();
        configService.reloadFromActivePayload(root.orElse(null));
    }

    @Override
    public void close() {
        scheduler.stopScheduler();
        db.close();
    }

    public static final class Config {
        public final Database database;
        public final Clock clock;
        public final IdGenerator ids;
        public final String masterKey;
        public final Path attachmentDir;
        public final Path updateRoot;
        public final Path logFile;
        public final Path machineIdFile;
        public final SignatureVerifier signatureVerifier;

        public Config(Database database, Clock clock, IdGenerator ids, String masterKey,
                      Path attachmentDir, Path updateRoot, Path logFile,
                      Path machineIdFile, SignatureVerifier signatureVerifier) {
            this.database = database;
            this.clock = clock;
            this.ids = ids;
            this.masterKey = masterKey;
            this.attachmentDir = attachmentDir;
            this.updateRoot = updateRoot;
            this.logFile = logFile;
            this.machineIdFile = machineIdFile;
            this.signatureVerifier = signatureVerifier;
        }
    }
}
