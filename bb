#!/usr/bin/perl -w

# BigBench - benchmark groups of opcodes under different versions/conditions

$| = 1;
$VERSION = '0.10';
use Math::BigFloat v1.41;
use File::Find;
use File::Spec;
use Getopt::Long;
use strict;
use vars qw/$VERSION/;

# default values:
my $data = 'bigint.def';		# input of definitions
my $duration = 2;			# how long to bench each op
my $digits = 3;				# output precision
my $path = '../old';			# where are the libraries?
my $skew = 1.0;				# scale all results by this
my $terse = 0;				# terse summary (only groups)?
my $inc = 'inc/perl';			# from where to read includes
my $help = 0;				# print help?
my $summary = 1;			# print summary?
my $base = 0;				# relative summary?
my $code = '';				# bench only this piece of code
my $unlink = 1;				# unlink tmp files?
my $integer = 1;			# round to integer?
my $tight = 0;				# tight spacing or not?
my $simulate = 0;			# simulate benchmark?
my $take = 'lowest';			# take this run
my $runs = 1;				# how many runs

my $starttime = time;

###############################################################################
# parse commandline options

print "BigBench v$VERSION  (c) Copyright by Tels 2001-2004.  Have fun!\n\n";

$help = 1 if @ARGV == 0;

die 'Error parsing commandline, use --help for more information'
 unless GetOptions(
 'templates=s' => \$inc, 'definitions=s' => \$data, 'path=s' => \$path,
 'duration=i' => \$duration, 'summary!' => \$summary, 'base=i' => \$base,
 'skew=f' => \$skew, 'terse!' => \$terse, 'tight!' => \$tight,
 'help' => \$help, 'code=s' => \$code, 'unlink!' => \$unlink,
 'simulate=i' => \$simulate, 'runs=i' => \$runs, 'take=s' => \$take,
 'accuracy=i' => \$digits, 'integer!' => \$integer,
 );

if ($help)
  {
  print <<HELP;
 Usage  : ./bb [options]
 Options: --help              print this screen and exit
          --accuracy=digits   round results to so many digits
          --base=number       print relative summary based on number
          --code=sourcecode   bench code snippet and ignore definitions
          --definitons=file   from where to read benchmark definitions
          --duration=seconds  run each op for at least this time
          --nosummary         don't print summary
          --nointeger         don't round results to integer
          --nounlink          don't unlink temporary files (for debug)
          --path=libpath      path to libraries used by templates
          --runs=number       run benchmark more than once (see --take)
          --simulate=sr       simulate results by using srand(sr)
          --skew=factor       scale reported numbers by factor
          --take=run          take lowest|average|highest|last
          --templates=path    path to templates to be used
          --terse             terse summary (unless --nosummary)
          --tight             more tight summary (smaller spacing)
 
 Options may be abbreviated, their case does not matter.

 Examples: ./bb --def=math.def --terse --skew=2.1	# better printable?	
           ./bb --def=str.def --inc=math --duration=5	# really fine-grained
           ./bb --def=some.def --nosummary		# detailed
           ./bb --def=some.def --terse --base=100	# simulate perlbench
           ./bb --code='"ababba" =~ /a+/;'		# only this
           ./bb --runs=2 --take=last			# cache, then bench
HELP

  exit;
  }

###############################################################################
# post-process commandline options

die ("Argument to --duration must be at least one\n") unless $duration >= 1;
die ("Argument to --runs must be at least one\n") unless $runs >= 1;
die ("Argument to --accuracy must be at least one\n") unless $digits >= 1;
$skew = Math::BigFloat->new($skew);
my $spacing = 2; $spacing = 1 if $tight == 1;
srand($simulate) if $simulate != 0;

# check take
$take =~ /^(average|lowest|highest|last)$/; $take = $1;
die ("Argument to --take must be one of 'lowest', 'average', 'highest' or 'last'\n")
 unless defined $take;

###############################################################################
# read definitions and templates

my ($tpl,$res,$tempfile);
my $group_cnt = 0; my $op_cnt = 0; my $empty_cnt = 0;
my $index = '0000';

print scalar localtime()," Reading templates from '$inc/'...";
find (\&wanted, $inc); 	# read all templates:
my $tpl_cnt = scalar keys %$tpl;
print "done.\n Got $tpl_cnt templates.\n";

die "Found no templates to benchmark.\n" if $tpl_cnt == 0;

my $longest_name = 6;

my $ops = {};
if ($code ne '')
  {
  print scalar localtime()," Benchmarking (with implicit --terse):\n";
  print " '$code'";
  $terse = 1;
  $op_cnt = 1; $group_cnt = 1; my $group = 1; my $id = 1;
  $ops->{$group} = {};
  $ops->{$group}->{name} = 'code';
  $ops->{$group}->{descr} = 'code';
  $ops->{$group}->{ops} = {};
  $ops->{$group}->{ops}->{$id} = {};
  my $g = $ops->{$group}->{ops}->{$id};
  $g->{id} = $id;
  $g->{name} = 'bb';
  $g->{setup} = '';
  $g->{empty} = '';
  $code =~ s/\\n/\n/;		# \n to real line ending
  $g->{bench} = $code;
  $g->{res} = {};
  foreach my $inc (keys %$tpl)
    {
    $g->{res}->{$inc} = {};
    }  
  }
else
  {
  $ops = load_definitions($data);
  } 

###############################################################################
# print preamble

print "\n Got $op_cnt op";
print "s" if $op_cnt > 1;
print " in $group_cnt group";
print "s" if $group_cnt > 1;
print ".\n";

die "Found no ops to benchmark.\n" if $op_cnt == 0;
 
print "\nEach op will run ";
print "$runs times " if $runs > 1;
print "for at least $duration seconds.\n";
print "From all runs the $take result will be taken.\n" if $runs > 1;
print "Results are scaled by factor $skew and rounded to $digits digits.\n";
print "Results will be rounded to integer.\n" if $integer != 0;

{
my $sec = int(($empty_cnt + $op_cnt) * $tpl_cnt * $duration * $runs * 1.5);
my $time;
$time = "$sec seconds" if $sec < 120;
$time = int($sec/60) . " minutes" if $sec < 2*3600 && $sec >= 120;
$time = int($sec/360)/10 . " hours" if $sec >= 2*3600;

print "The benchmark will run for approximately $time.\n";
}

my $benchmarks = $op_cnt * $tpl_cnt;

print "Benchmark results are simulated with srand($simulate);\n"
      if $simulate != 0;

###############################################################################
# run benchmark

my ($o,$name); my $failed = 0;
foreach my $inc (sort keys %$tpl)
  {
  print "\nRunning '$inc':\n";
  foreach my $group (sort { $a <=> $b} keys %$ops)
    {
    print " Benchmarking group $group ('$ops->{$group}->{name}'):\n";
    $ops->{$group}->{sum}->{$inc} = Math::BigFloat->bzero();
    my $count = scalar keys %{$ops->{$group}->{ops}};
    print "Warning, no ops in group $group ('$ops->{$group}->{name}'\n"
     if $count == 0;
    my $group_na = 0;
    foreach my $id (sort { $a <=> $b} keys %{$ops->{$group}->{ops}})
      {
      $o = $ops->{$group}->{ops}->{$id};
      my $i = "  $id"; while (length($i) < 7) { $i = ' '.$i; }
      $name = $o->{name}; $name .= ' ' while (length($name) < $longest_name);
      print "$i  $name";
      my $empty = Math::BigFloat->bzero(0);		# no correct yet
      if ($o->{empty} ne '')
        {
	$empty = run_bench($inc,$o->{setup},$o->{empty});
	}
      my $result = run_bench($inc,$o->{setup},$o->{bench});
      #	store result for every inc separately
      $o->{res}->{$inc} = {} if !defined $o->{res}->{$inc};
      $o = $o->{res}->{$inc};
      $result *= $skew; $empty *= $skew;
      $o->{both_result} = $result;
      $o->{empty_result} = $empty;
      # no result, or empty loop slower than full
      if ($result < 0)
        {
        $o->{real_result} = 'n/a'; $failed++;
        print "         n/a (got no result)\n"; next;
        }
      if ($empty != 0 && $empty <= $result)
        {
        $o->{real_result} = ' -- '; $failed++;
        print "         n/a (no accurate timing)\n"; next;
        }
      $group_na = 1;			# got some not n/a result
      $o->{real_result} = $o->{both_result};
      $o->{real_result} = 1/(1/$result - 1/$empty) if $empty > 0;
      $ops->{$group}->{sum}->{$inc} += $o->{real_result}; # sum before rounding
      $o->{real_result}->bround($digits);
      $o->{empty_result}->bround($digits);
      $o->{both_result}->bround($digits);
      $name = $o->{real_result};
      $name->bfround(0) if $integer != 0;
      # 12300.000 => '12300    ', but 0.340 => 0.340
      # $name =~ s/\.(0+)$/' '. ' ' x length($1);/e;
      print pad($name,' ',12,1), ' ops/s';
      print "\n" and next if $empty == 0;
      print " (empty: $o->{empty_result},";
      print " both: $o->{both_result})";
      print "\n";
      }
    if ($group_na == 0)
      {
      $o = 'n/a'; $ops->{$group}->{sum}->{$inc} = $o;
      }
    else
      {
      $o = $ops->{$group}->{sum}->{$inc} / $count;
      $o->bround($digits);
      $ops->{$group}->{sum}->{$inc} = $o;	# store rounded
      }
    $o = ' '.$o while (length($o) < 10);
    print " Average:  ",' ' x $longest_name,"$o";
    print " ops/s" if $group_na;
    print "\n";
    }
  }

print "\n$failed out of $benchmarks (",
       Math::BigFloat->new(100*$failed)->fdiv($benchmarks)->bfround(-2),
      "%) benchmarks failed to return a result.\n"
 if $failed > 0 && $benchmarks > 0;

my $endtime = time;

exit unless $summary;
 
###############################################################################
# create a summary table with all the results

my $first = ''; my $finc;
print "\n", scalar localtime();
if ($base == 0)
  {
  print " Numbers are absolute ops/s, scaled by factor $skew.\n\n";
  }
else
  {
  # find out first template
  $first = [ sort keys %$tpl ]->[0];
  print " Numbers are relative to $first, $base denotes 100%.\n\n";
  # remove $first from $tpl so that it won't get printed
  $finc = $tpl->{$first};
  delete $tpl->{$first};
  }
if ($integer == 0)
  {
  print " Numbers are rounded to integer.\n";
  }

### first the table header
my $parts = 0;
my $line = '-' x ($longest_name + 2) .'+';	# row of '-'
my $head = " " x ($longest_name+3) . '|';
print $head;
my $len = {};
foreach my $inc (sort keys %$tpl)
  {
  my @ps = split /_/,$inc;			# how many parts?
  $parts = scalar @ps if scalar @ps > $parts;
  # find longest part from inc part names, at least 5
  my $l = 5; foreach (@ps) { $l = length($_) if length($_) > $l; }

  # now see if any result is even longer
  # while we are it, compute relative result to be output
  foreach my $group (keys %$ops)
    {
    foreach my $id (keys %{$ops->{$group}->{ops}})
      {
      my $go = $ops->{$group}->{ops}->{$id}->{res};
      my $o = $go->{$inc}->{real_result};
      if ($base != 0)
	{
	# convert relative to $base
        my $o1 = $go->{$first}->{real_result};
	if (ref($o) && ref($o1))
	  {
	  $o = $base * $o / $o1;
	  }
	else
	  {
          $o = 'n/a';
	  }
	}
      $o->bround($digits) if ref($o);
      $go->{$inc}->{output} = $o;
      $l = length($o) if length($o) > $l;
      }
    # now same for group average
    my $go = $ops->{$group}->{sum};
    my $o = $go->{$inc};
    if ($base != 0)
      {
      # convert relative to $base
      my $o1 = $go->{$first};
      if (ref($o) && ref($o1))
        {
	$o = $base * $o / $o1;
        }
      else
	{
        $o = 'n/a';
	}
      }
    $o->bround($digits) if ref($o);
    $ops->{$group}->{output}->{$inc} = $o;
    $l = length($o) if length($o) > $l;

    }
  $l += $spacing;

  $len->{$inc} = $l;				# remember on a per inc base
  $inc =~ s/_.*//;				# remove all other parts
  $inc = pad($inc,' ',$l,1);
  $line .= '-' x length($inc);
  print $inc;
  }
$line .= '-';		# one more
print "\n";

# now print multiple lines for all the other parts
my $p = 1;
while ($p < $parts)
  {
  print $head;
  foreach my $inc (sort keys %$tpl)
    {
    my @ps = split /_/,$inc;			# all parts
    my $i = $ps[$p]||'';
    $i = pad($i,' ',$len->{$inc},1);
    print $i;
    }
  print "\n";
  $p++;	# next part
  }

# last header line is a row of dashes
print " $line\n";

$line =~ s/[-]/\./g; $line =~ s/[+]/\|/g;	# separator line is '..|...'
# now the table body
foreach my $group (sort { $a <=> $b} keys %$ops)
  {
 
  if (!$terse)
    {
    # print op(s)
    foreach my $id (sort { $a <=> $b} keys %{$ops->{$group}->{ops}})
      {
      $name = '  ' . $ops->{$group}->{ops}->{$id}->{name};
      print pad($name,' ',$longest_name+3), '|';
      my $go = $ops->{$group}->{ops}->{$id}->{res};
      foreach my $inc (sort keys %$tpl)
        {
        my $o = $go->{$inc}->{output};
        print pad($o,' ',$len->{$inc},1);
        }
      print "\n";
      }
    } # end no terse
  
  # print group average(s)
  print pad(' ' . $ops->{$group}->{name}.':',' ',$longest_name+3),'|';
  my $go = $ops->{$group}->{output};
  foreach my $inc (sort keys %$tpl)
    {
    my $o = $go->{$inc};
    print pad($o,' ',$len->{$inc},1);
    }
  print "\n";
  print " $line\n" unless $terse;
  }


1;

###############################################################################
# subs

sub run_bench
  {
  my ($inc,$setup,$bench) = @_;

  my $temp = ${$tpl->{$inc}};
  $temp .= "$setup;\ntimethese ( -$duration, {\n bb => sub { $bench }\n } );";
  $temp =~ s/##path##/$path/g;

  my $devnull = File::Spec->devnull();
  my $tempfile = "bb_$index.tmp";
  my $rc;
  my @results;
  if ($simulate == 0)
    {
    write_file ($tempfile,\$temp);
    chmod 0755,$tempfile;
    foreach (1..$runs)
      {
      $rc = `./$tempfile 2>$devnull`;
      $rc =~ /bb:.*\@\s+([0-9]+[0-9\.]+)\/s/;		# extract result
      push @results, Math::BigFloat->new($1 || -1);
      }
    unlink $tempfile if -e $tempfile && $unlink == 1;
    }
  else
    {
    foreach (1..$runs)
      {
      push @results, Math::BigFloat->new( rand(100000)+100 );
      }
    }
  # select the proper run
  $rc = $results[0];				# default
  $rc = $results[-1] if $take eq 'last';	# last run
  if ($runs > 1 && $take ne 'last')		# highest, lowest or average 
    {
    my $m = 0;
    if ($take =~ /^(lowest|highest)$/)
      {
      my $i = 0;
      foreach (@results)
        {
        $m = $i if $take eq 'lowest' && $_ < $results[$m];
        $m = $i if $take eq 'highest' && $_ > $results[$m];
        }
      $rc = $results[$m];
      }
    else
      {
      $rc = Math::BigFloat->fzero();
      foreach (@results)
        {
        $rc += $_;	# sum for average;
        }
      $rc->fdiv(scalar @results);
      }
    }
  $index++;
  if (wantarray)
    {
    return ($rc,@results);
    }
  return $rc;
  }

sub load_definitions
  {
  my $data = shift;

  print scalar localtime()," Reading definitions from $data...";
  # read definition file
  open FILE, $data or die "Can't read $data: $!\n";
  my $ops = {}; my $line_nr = 0; my $group;
  my $last_gid = 0;
  my $last_oid = 0;
  while (<FILE>)
    {
    $line_nr++;
    chomp();
    next if $_ =~ /^#/;		# no comments
    next if $_ =~ /^\s*$/;	# no empty lines

    if ($_ =~ /^group=/)
      {
      $_ =~ s/^group=//;
      my ($name,$id,$descr) = split /#/;
      $id = ++$last_gid if ($id||0) == 0;
      die ("\nRedefinition of group $id at $data, line $line_nr\n")
      if defined $ops->{$id};
      $group_cnt++;
      $group = $id;		# remember
      $last_gid = $id;
      $ops->{$id} = {};
      $ops->{$id}->{name} = $name;
      $longest_name = length($name) if length($name) > $longest_name;
      $ops->{$id}->{descr} = $name;
      $ops->{$id}->{ops} = {};
      next;
      }
    die ("\nGroup definition must come before op definition at line $line_nr\n")
     unless defined $group;
    my ($id,$name,$setup,$empty,$bench) = split /#/;
    $id = ++$last_oid if ($id||0) == 0;
    die ("\nRedefinition of op $id in group $group at $data, line $line_nr\n")
      if defined $ops->{$group}->{ops}->{$id};
    $last_oid = $id; $op_cnt++;
    $ops->{$group}->{ops}->{$id} = {};
    my $g = $ops->{$group}->{ops}->{$id};
    $g->{id} = $id;
    $longest_name = length($name) if length($name) > $longest_name;
    $g->{name} = $name;
    $setup =~ s/\\n/\n/;		# \n to real line ending
    $g->{setup} = $setup;
    $empty =~ s/\\n/\n/;		# \n to real line ending
    $g->{empty} = $empty;
    $empty_cnt++ if $empty ne '';
    $bench =~ s/\\n/\n/;		# \n to real line ending
    $g->{bench} = $bench;
    $g->{res} = {};
    foreach my $inc (keys %$tpl)
      {
      $g->{res}->{$inc} = {};
      }
    }
  close FILE;
  print "done.";
  return $ops;
  }

sub pad
  {
  my ($name,$pad,$len,$dir) = @_;

  $pad = ' ' unless defined $pad; 
  $dir = $dir || 0;
  if ($dir == 0)
    {
    $name .= $pad while (length($name) < $len); 
    }
  else
    {
    $name = $pad.$name while (length($name) < $len); 
    }
  return $name;
  }

sub write_file
  {
  my ($file,$data) = @_;

  open FILE, ">$file" or die "Can't write $file: $!\n";
  print FILE $$data;
  close FILE;
  }

sub read_file
  {
  my $data = shift;

  # read a template file
  open FILE, $data or die "Can't read $data: $!\n";
  my $doc = '';
  while (<FILE>)
    {
    $doc .= $_;
    }
  close FILE;
  return \$doc;
  }

sub wanted
  {
  return unless -f;

  return unless $_ =~ /\.inc$/;
  my $file = $_;
  my $inc = $file; $inc =~ s/\.inc$//;
  $tpl->{$inc} = read_file($file);
  }

END 
  {
  unlink $tempfile if defined $tempfile && -e $tempfile && $unlink == 1;
  $endtime = time unless defined $endtime;
  my $seconds = $endtime - $starttime;
  print "\n", scalar localtime(), " All done, took $seconds seconds. Enjoy!\n\n";
  }

__END__

=pod

=head1 NAME

BigBench - benchmark groups of opcodes under different versions/conditions

=head1 SYNOPSIS

 Usage  : ./bb [options]
 Options: --help              print this screen and exit
          --accuracy=digits   round results to so many digits
          --base=number       print relative summary based on number
          --code=sourcecode   bench code snippet and ignore definitons
          --definitons=file   from where to read benchmark definitions
          --duration=seconds  run each op for at least this time
          --nosummary         don't print summary
          --nounlink          don't unlink temporary files (for debug)
          --path=libpath      path to libraries used by templates
          --runs=number       run benchmark more than once (see --take)
          --simulate=sr       simulate results by using srand(sr)
          --skew=factor       scale reported numbers by factor
          --take=run          take lowest|average|highest|last
          --templates=path    path to templates to be used
          --terse             terse summary (unless --nosummary)
          --tight             more tight summary (smaller spacing)
 
 Options may be abbreviated, their case does not matter.

=head1 DESCRIPTION

BigBench let's you define groups of opcodes (source code snippets), which are
placed into different templates and then benchmarked. The definitons are
stored in a file and can thus be re-used and allow to re-do benchmarks with
reliable results. It is also possible to run a short benchmark snippet directly
from the commandline.

The templates can load different module or Perl versions, or just set different
flags. This allows comparisations between versions.

You can specify on a per-op basis, unlike with C<use Benchmark;>, what the
empty loop should look and how the benchmark should be setup.

The benchmark results are rounded, and a nice summary is printed.

There are many options which control how long to run benchmarks, how the
results are rounded, summary should look like etc.

=head1 OPTIONS

The following commandline options exists:

=over 2

=item --help

Print the screen and exit. All other options are ignored.

=item --accuracy=digits

Round results to so many digits. Default is 3.

=item --base=number

Print a relative summary based on number, not with absolute results. If you
do C<--base=100>, you can simulate perlbench.

=item --code=sourcecode

Bench the given source code snippet. A C<--definitions=file> will be ignored.

=item --definitons=file

From which file to read the benchmark definitions. Defaults to 'bigint.def'.

=item --duration=seconds

Run each op for at least this time (in seconds). Must be at least one second.
The timings are quite accurate with only 2 or 3 seconds, so going higher will
not give you much better results. If you want just a quick peek on what the
output will look like, use C<--simulate=sr> (see there).

=item --nosummary

Don't print summary. When given, C<--terse> and C<--tight> are ignored.

=item --nounlink

Don't unlink temporary files. If something goes wrong, you can have a look at
what bb creates.

=item --path=libpath

Specifie the path to the libraries used by templates. You can put unpacked
modules or Perl versions there. This string will be interpolated into
C<##path##> in the template files.

=item --runs=number

Run each benchmark so many times. See also L</--take> for what result will
be taken.

=item --simulate=sr

Simulate results by using srand(sr). This is a quick way to see how the output
will look like and also used by the testsuite.

=item --skew=factor

Scale all reported numbers by this factor. For more nicer output.

=item --take=run

Take the run that is 'lowest', 'average', 'highest' or the 'last'.

Generally, when doing multiply runs, you want to take the highest run. The
reason is that any noise introduced by the system while running the benchmarks
makes the numbers worse, and since the results are in ops/s, the highest number
represents the peak performance, which comes as closest as possible to the
real performance.

You want to take the average of all runs when you want to find out the average
performance that can be expected on a typical system.

The worst perfomance can be found by taking the lowest run.

The last run is for benchmarks that involve things that get cached (f.i.
filesystem dependend benchmarks). In this case, --take=last --runs=2 will get
the first run to cache the things, discard it's result and use the one from the
second run.

See also L</--runs>.

=item --templates=path

Specifies the path to the templates to be used for the actual benchmarking.

=item --terse

Create a more terse summary. It will only print out the group averages, and
suppress the actual op results.

=item --tight

Create a more tight summary with smaller spacing.

=back

=head1 Examples

 ./bb --def=math.def --terse --skew=2.1		# better printable?	
 ./bb --def=str.def --inc=math --duration=5	# really fine-grained
 ./bb --def=some.def --nosummary		# detailed
 ./bb --def=some.def --terse --base=100		# simulate perlbench
 ./bb --code='"ababba" =~ /a+/;'		# only this

=head1 LICENSE

This program is free software; you may redistribute it and/or modify it under
the same terms as Perl itself.

=head1 AUTHORS

Tels http://bloodgate.com in late 2001. (C) Tels 2001-2004. 

=cut

