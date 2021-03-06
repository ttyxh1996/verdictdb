import getpass
import git
import glob
import re
import os
from subprocess import call
from update_build_number import current_version
import yaml

script_dir = os.path.dirname(os.path.realpath(__file__))
verdict_doc_dir = os.path.join(script_dir, "../../verdict-doc")
verdict_site_dir = os.path.join(script_dir, "../../verdict-site")
sourceforge_scp_base_url = "frs.sourceforge.net:/home/frs/project/verdict"
sourceforge_download_base_url = "https://sourceforge.net/projects/verdict/files"
push_to_git = False
supported_platforms = yaml.load(open(os.path.join(script_dir, 'supported_platforms.yml')))

def get_version_string(j_version):
    return "%s.%s.%s" % (j_version['major'], j_version['minor'], j_version['build'])

def get_cli_zip_filename(platform, j_version):
    # return 'verdict-cli-%s-%s.zip' % (platform, get_version_string(j_version))
    return 'verdict-cli-%s.zip' % (get_version_string(j_version))

def remove_cli_zip(j_version):
    print 'removes cli zip file.'
    for f in glob.glob('verdict*.zip'):
        call(['rm', f])

def zip_command_line_interface(j_version):
    zip_name = get_cli_zip_filename(None, j_version)
    print 'creating a zip archive: %s' % zip_name
    folder_name = 'verdict-cli-%s' % get_version_string(j_version)
    call(['rm', '-rf', folder_name])
    call(['mkdir', folder_name])

    files_to_copy = ['README.md',
                     'LICENSE',
                     'bin',
                     'jars',
                     'jdbc_jars',
                     'conf']

    for f in files_to_copy:
        call(['cp', '-r', f, folder_name])

    call(['zip', '-r', zip_name, folder_name])

    # for family, versions in supported_platforms.iteritems():
    #     for v, env in versions.iteritems():
    #         if 'shell' not in env:
    #             continue
    #         zip_name = get_cli_zip_filename(v, j_version)
            

def update_verdict_site(j_version):
    """
    The download page in the verdict documentation should include
    1. the correct link to the repository
    2. the correct compiled jar files.
    """
    print 'updates verdict site.'
    sf_url = 'https://sourceforge.net/projects/verdict/files/%d.%d' % (j_version['major'], j_version['minor'])
    g = git.cmd.Git(verdict_site_dir)
    g.pull()
    verdict_site_conf_file = os.path.join(verdict_site_dir, '_config.yml')

    y = yaml.load(open(verdict_site_conf_file))
    y['url'] = "http://verdictdb.org/"
    y['version'] = get_version_string(j_version)

    # spark (core)
    yspark = {}
    # for family, versions in supported_platforms.iteritems():
    #     for v, env in versions.iteritems():
    #         if 'spark' not in env:
    #             continue
    #         yspark[v] = {}
    #         yspark[v]['family'] = family
    #         yspark[v]['name'] = 'verdict-spark-lib-%s-%s.jar' % (v, get_version_string(j_version))
    #         yspark[v]['url'] = '%s/verdict-spark-lib-%s-%s.jar/download' % (sf_url, v, get_version_string(j_version))
    yspark['family'] = 'Verdict Spark Library'
    yspark['name'] = 'verdict-spark-lib-%s.jar' % get_version_string(j_version)
    yspark['url'] = '%s/verdict-spark-lib-%s.jar/download' % (sf_url, get_version_string(j_version))
    y['verdict_spark'] = yspark

    # jdbc
    yjdbc = {}
    # for family, versions in supported_platforms.iteritems():
    #     for v, env in versions.iteritems():
    #         if 'jdbc' not in env:
    #             continue
    #         yjdbc[v] = {}
    #         yjdbc[v]['family'] = family
    #         yjdbc[v]['name'] = 'verdict-jdbc-%s-%s.jar' % (v, get_version_string(j_version))
    #         yjdbc[v]['url'] = '%s/verdict-jdbc-%s-%s.jar/download' % (sf_url, v, get_version_string(j_version))
    yjdbc['family'] = 'Verdict JDBC Driver'
    yjdbc['name'] = 'verdict-spark-lib-%s.jar' % get_version_string(j_version)
    yjdbc['url'] = '%s/verdict-jdbc-%s.jar/download' % (sf_url, get_version_string(j_version))
    y['verdict_jdbc'] = yjdbc

    # shell
    yshell = {}
    # for family, versions in supported_platforms.iteritems():
    #     for v, env in versions.iteritems():
    #         if 'shell' not in env:
    #             continue
    #         yshell[v] = {}
    #         yshell[v]['family'] = family
    #         yshell[v]['name'] = 'verdict-cli-%s-%s.zip' % (v, get_version_string(j_version))
    #         yshell[v]['url'] = '%s/verdict-cli-%s-%s.zip/download' % (sf_url, v, get_version_string(j_version))
    yshell['family'] = 'Command Line Interface (including Verdict JDBC Driver)'
    yshell['name'] = 'verdict-cli-%s.jar' % get_version_string(j_version)
    yshell['url'] = '%s/verdict-cli-%s.zip/download' % (sf_url, get_version_string(j_version))
    y['verdict_shell'] = yshell

    with open(verdict_site_conf_file, 'w') as fout:
        fout.write("# auto generated by release/release_jars.py\n\n")
        fout.write(yaml.dump(y, default_flow_style=False))
        
    try:
        g.execute(['git', 'commit', '-am', 'version updated to %s' % get_version_string(j_version)])
    except git.exc.GitCommandError:
        pass
    if push_to_git:
        g.push()

def update_verdict_doc(j_version):
    """
    The download page in the verdict documentation should include
    1. the correct link to the repository
    2. the correct compiled jar files.
    """
    print 'updates verdict documentation.'
    g = git.cmd.Git(verdict_doc_dir)
    g.pull()
    verdict_doc_conf_file = os.path.join(verdict_doc_dir, 'conf.py')
    version_str = '%s.%s.%s' % (j_version['major'], j_version['minor'], j_version['build'])
    lines = [l for l in open(verdict_doc_conf_file)]
    updated_lines = []
    for l in lines:
        result1 = re.match("version = .*", l)
        result2 = re.match("release = .*", l)
        if result1 is None and result2 is None:
            updated_lines.append(l)
        elif result1:
            updated_lines.append("version = u'%s'\n" % (version_str))
        elif result2:
            updated_lines.append("release = u'%s'\n" % (version_str))
    with open(verdict_doc_conf_file, 'w') as conf_file_out:
        conf_file_out.write("".join(updated_lines))
    try:
        g.execute(['git', 'commit', '-am', 'version updated to %s' % version_str])
    except git.exc.GitCommandError:
        pass
    if push_to_git:
        g.push()

def get_path_to_files_to_upload(j_version):
    paths = []
    jars_dir = os.path.join(script_dir, '../jars')
    # get_version_string(j_version)

    paths.append(os.path.join(jars_dir, 'verdict-spark-lib-%s.jar' % get_version_string(j_version)))
    paths.append(os.path.join(jars_dir, 'verdict-jdbc-%s.jar' % get_version_string(j_version)))
    paths.append(get_cli_zip_filename(None, j_version))

    # for family, versions in supported_platforms.iteritems():
    #     for v, env in versions.iteritems():
    #         if 'spark' in env:
    #             paths.append(os.path.join(jars_dir, 'verdict-spark-lib-%s-%s.jar' % (v, get_version_string(j_version))))
    #         if 'jdbc' in env:
    #             paths.append(os.path.join(jars_dir, 'verdict-jdbc-%s-%s.jar' % (v, get_version_string(j_version))))
    #         if 'shell' in env:
    #             paths.append(get_cli_zip_filename(v, j_version))
    return paths

def call_with_failure(cmd):
    ret = call(cmd)
    if ret != 0:
        raise ValueError('shell return code indicates a failure.')

def create_sourceforge_dir_if_not_exists(j_version):
    print 'creates a version-specific folder if not exists.'
    v = "%d.%d" % (j_version['major'], j_version['minor'])
    mkdir_str = "mkdir -p /home/frs/project/verdict/%s" % v
    call_with_failure(['ssh', "yongjoop,verdict@shell.sourceforge.net", 'create'])
    call_with_failure(['ssh', "yongjoop,verdict@shell.sourceforge.net", mkdir_str])

def upload_file_to_sourceforge(path, j_version):
    """
    Upload
    1. core jar
    2. jdbc jar
    3. command-line interface zip
    """
    major = j_version['major']
    minor = j_version['minor']
    major_minor = "%d.%d" % (major, minor)
    target = 'yongjoop@%s/%s/' % (sourceforge_scp_base_url, major_minor)
    print 'uploads %s to the %s.' % (path, target)
    call_with_failure(['scp', path, target])

def return_true_for_jenkins():
    if getpass.getuser() == 'jenkins':
        'Changes to the verdict websites (and documentation pages) will be pushed to their repositories.'
        return True
    else:
        'Changes to the verdict websites (and documentation pages) won\'t be pushed.'
        return False

if __name__ == "__main__":
    push_to_git = return_true_for_jenkins()

    j_version = current_version()
    zip_command_line_interface(j_version)
    # create_sourceforge_dir_if_not_exists(j_version)
    file_paths = get_path_to_files_to_upload(j_version)
    for p in file_paths:
        upload_file_to_sourceforge(p, j_version)
    update_verdict_doc(j_version)
    update_verdict_site(j_version)
    remove_cli_zip(j_version);

